package com.example.demo.pubsub;

import com.example.demo.model.ChatMessage;
import com.example.demo.monitoring.service.WebSocketFanoutMetrics;
import com.example.demo.service.WebSocketSessionRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class RedisSubscriber implements MessageListener {

    private final RedisTemplate<String, Object> redisTemplate; // RedisTemplate을 주입받아 사용
    private final SimpMessagingTemplate messagingTemplate; // 특정 Broker로 메시지를 전달
    private final WebSocketSessionRegistry webSocketSessionRegistry;
    private final WebSocketFanoutMetrics fanoutMetrics;

    // TALK 메시지 fan-out 전용 실행기 튜닝값들
    // 목적: 시스템 메시지(ENTER/QUIT)와 작업 큐를 분리해 혼잡 전파를 줄이기 위함
    @Value("${chat.fanout.talk.executor.core-pool-size:8}")
    private int talkExecutorCorePoolSize;
    @Value("${chat.fanout.talk.executor.max-pool-size:32}")
    private int talkExecutorMaxPoolSize;
    @Value("${chat.fanout.talk.executor.queue-capacity:10000}")
    private int talkExecutorQueueCapacity;
    @Value("${chat.fanout.system.executor.core-pool-size:2}")
    private int systemExecutorCorePoolSize;
    @Value("${chat.fanout.system.executor.max-pool-size:8}")
    private int systemExecutorMaxPoolSize;
    @Value("${chat.fanout.system.executor.queue-capacity:2000}")
    private int systemExecutorQueueCapacity;
    // fan-out 큐 포화 시 적용할 backpressure 정책
    @Value("${chat.fanout.backpressure.policy:drop-newest}")
    private String backpressurePolicyRaw;
    @Value("${chat.fanout.backpressure.disconnect-count:1}")
    private int backpressureDisconnectCount;
    @Value("${chat.fanout.backpressure.disconnect-reason:Slow consumer backpressure}")
    private String backpressureDisconnectReason;
    // TALK 메시지 마이크로배치(짧은 시간/개수 단위 묶음 전송) 설정
    @Value("${chat.fanout.talk.batch.enabled:false}")
    private boolean talkBatchEnabled;
    @Value("${chat.fanout.talk.batch.max-size:20}")
    private int talkBatchMaxSize;
    @Value("${chat.fanout.talk.batch.max-wait-ms:20}")
    private int talkBatchMaxWaitMs;

    private ThreadPoolExecutor talkFanoutExecutor;
    private ThreadPoolExecutor systemFanoutExecutor;
    private BackpressurePolicy backpressurePolicy;
    private ScheduledExecutorService talkBatchScheduler;
    private final ConcurrentHashMap<String, TalkBatchBuffer> talkBatchBuffers = new ConcurrentHashMap<>();

    public RedisSubscriber(
            RedisTemplate<String, Object> redisTemplate,
            SimpMessagingTemplate messagingTemplate,
            WebSocketSessionRegistry webSocketSessionRegistry,
            WebSocketFanoutMetrics fanoutMetrics
    ) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
        this.fanoutMetrics = fanoutMetrics;
    }

    @PostConstruct
    public void init() {
        // 설정 문자열을 enum 정책으로 고정해 런타임 분기 비용/실수 여지를 줄인다.
        this.backpressurePolicy = BackpressurePolicy.from(backpressurePolicyRaw);
        this.talkFanoutExecutor = buildExecutor(
                "fanout-talk-",
                talkExecutorCorePoolSize,
                talkExecutorMaxPoolSize,
                talkExecutorQueueCapacity
        );
        this.systemFanoutExecutor = buildExecutor(
                "fanout-system-",
                systemExecutorCorePoolSize,
                systemExecutorMaxPoolSize,
                systemExecutorQueueCapacity
        );
        // 배치 flush 타이머는 단일 스레드로 운영해 방별 버퍼 상태를 단순하게 유지한다.
        this.talkBatchScheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            private final AtomicInteger sequence = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "fanout-talk-batch-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });
        log.info(
                "Initialized RedisSubscriber fan-out executors. policy={}, talk(core={},max={},queue={}), system(core={},max={},queue={})",
                backpressurePolicy,
                talkExecutorCorePoolSize,
                talkExecutorMaxPoolSize,
                talkExecutorQueueCapacity,
                systemExecutorCorePoolSize,
                systemExecutorMaxPoolSize,
                systemExecutorQueueCapacity
        );
        log.info(
                "Initialized TALK micro-batch. enabled={}, maxSize={}, maxWaitMs={}",
                talkBatchEnabled,
                talkBatchMaxSize,
                talkBatchMaxWaitMs
        );
    }

    /**
     * Redis에서 메시지가 발행(publish)되면 대기하고 있던 onMessage가 해당 메시지를 받아 처리
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // redis에서 발행된 데이터를 받아 deserialize
            ChatMessage chatMessage = (ChatMessage) redisTemplate.getValueSerializer().deserialize(message.getBody());

            // WebSocket 구독자에게 채팅 메시지 발송
            if (chatMessage != null) {
                fanoutMetrics.recordRedisMessageReceived(chatMessage);
                dispatchFanout(chatMessage);
            }
        } catch (Exception e) {
            fanoutMetrics.recordRedisDeserializeError();
            log.error("Error deserializing message or sending to WebSocket: {}", e.getMessage(), e);
        }
    }

    private void dispatchFanout(ChatMessage chatMessage) {
        boolean talk = chatMessage.getType() == ChatMessage.MessageType.TALK;
        // TALK만 배치 대상: 시스템 메시지는 즉시성이 중요하므로 배치하지 않는다.
        if (talk && talkBatchEnabled) {
            enqueueTalkBatch(chatMessage);
            return;
        }
        ThreadPoolExecutor executor = talk ? talkFanoutExecutor : systemFanoutExecutor;
        Runnable task = () -> fanoutToWebSocket(chatMessage);
        int queueDepth = executor.getQueue().size();

        try {
            executor.execute(task);
            fanoutMetrics.recordFanoutEnqueued(talk, queueDepth);
        } catch (RejectedExecutionException rejectedExecutionException) {
            // 큐 포화 시 정책 기반 처리(drop/disconnect)로 메모리 무한증가를 막는다.
            applyBackpressurePolicy(executor, task, talk, rejectedExecutionException);
        }
    }

    private void enqueueTalkBatch(ChatMessage chatMessage) {
        String roomId = chatMessage.getRoomId();
        if (roomId == null || roomId.isBlank()) {
            log.warn(
                    "Drop TALK message with blank roomId. type={}, msgId={}",
                    chatMessage.getType(),
                    chatMessage.getMsgId()
            );
            return;
        }
        TalkBatchBuffer buffer = talkBatchBuffers.computeIfAbsent(roomId, ignored -> new TalkBatchBuffer());
        List<ChatMessage> toFlush = null;
        synchronized (buffer) {
            buffer.messages.add(chatMessage);
            // 첫 메시지가 들어온 시점에 flush 예약: 짧은 지연으로 묶어 보내도록 한다.
            if (buffer.messages.size() == 1) {
                long delayMs = Math.max(1, talkBatchMaxWaitMs);
                buffer.scheduledFlush = talkBatchScheduler.schedule(() -> flushTalkBatch(roomId), delayMs, TimeUnit.MILLISECONDS);
            }
            // 최대 개수 도달 시 즉시 flush하여 대기시간 상한을 제어한다.
            if (buffer.messages.size() >= Math.max(1, talkBatchMaxSize)) {
                toFlush = drainBufferLocked(buffer);
            }
        }
        if (toFlush != null && !toFlush.isEmpty()) {
            submitTalkBatch(roomId, toFlush);
        }
    }

    private void flushTalkBatch(String roomId) {
        TalkBatchBuffer buffer = talkBatchBuffers.get(roomId);
        if (buffer == null) {
            return;
        }
        List<ChatMessage> toFlush;
        synchronized (buffer) {
            toFlush = drainBufferLocked(buffer);
            if (buffer.messages.isEmpty() && buffer.scheduledFlush == null) {
                talkBatchBuffers.remove(roomId, buffer);
            }
        }
        if (!toFlush.isEmpty()) {
            submitTalkBatch(roomId, toFlush);
        }
    }

    private List<ChatMessage> drainBufferLocked(TalkBatchBuffer buffer) {
        List<ChatMessage> drained = new ArrayList<>(buffer.messages);
        buffer.messages.clear();
        ScheduledFuture<?> future = buffer.scheduledFlush;
        if (future != null) {
            future.cancel(false);
            buffer.scheduledFlush = null;
        }
        return drained;
    }

    private void submitTalkBatch(String roomId, List<ChatMessage> messages) {
        int queueDepth = talkFanoutExecutor.getQueue().size();
        Runnable task = () -> fanoutBatchToWebSocket(roomId, messages);
        try {
            talkFanoutExecutor.execute(task);
            fanoutMetrics.recordFanoutEnqueued(true, queueDepth);
        } catch (RejectedExecutionException rejectedExecutionException) {
            applyBackpressurePolicy(talkFanoutExecutor, task, true, rejectedExecutionException);
        }
    }

    private void fanoutToWebSocket(ChatMessage chatMessage) {
        long startedAt = System.nanoTime();
        Throwable throwable = null;
        try {
            messagingTemplate.convertAndSend("/sub/chat/room/" + chatMessage.getRoomId(), chatMessage);
        } catch (Exception exception) {
            throwable = exception;
            log.warn(
                    "Failed to fan-out message. roomId={}, type={}, msgId={}",
                    chatMessage.getRoomId(),
                    chatMessage.getType(),
                    chatMessage.getMsgId(),
                    exception
            );
        } finally {
            // convertAndSend 호출 시간을 기록해 fan-out 지연 분포를 추적한다.
            fanoutMetrics.recordFanoutConvertResult(System.nanoTime() - startedAt, throwable);
        }
    }

    private void fanoutBatchToWebSocket(String roomId, List<ChatMessage> messages) {
        long startedAt = System.nanoTime();
        Throwable throwable = null;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "BATCH");
            payload.put("roomId", roomId);
            payload.put("count", messages.size());
            payload.put("messages", messages);
            messagingTemplate.convertAndSend("/sub/chat/room/" + roomId, (Object) payload);
        } catch (Exception exception) {
            throwable = exception;
            log.warn("Failed to fan-out batched TALK messages. roomId={}, count={}", roomId, messages.size(), exception);
        } finally {
            fanoutMetrics.recordFanoutConvertResult(System.nanoTime() - startedAt, throwable);
        }
    }

    private void applyBackpressurePolicy(
            ThreadPoolExecutor executor,
            Runnable incomingTask,
            boolean talk,
            RejectedExecutionException rejectedExecutionException
    ) {
        switch (backpressurePolicy) {
            case DROP_OLDEST -> {
                // 가장 오래 대기 중인 작업을 버리고 최신 작업을 살리는 전략
                Runnable droppedTask = executor.getQueue().poll();
                if (droppedTask != null) {
                    fanoutMetrics.recordDroppedOldest();
                } else {
                    fanoutMetrics.recordDroppedNewest();
                }
                try {
                    executor.execute(incomingTask);
                    fanoutMetrics.recordFanoutEnqueued(talk, executor.getQueue().size());
                } catch (RejectedExecutionException retryException) {
                    fanoutMetrics.recordDroppedNewest();
                    log.debug("Fan-out queue still full after DROP_OLDEST retry. policy={}", backpressurePolicy, retryException);
                }
            }
            case DROP_NEWEST -> {
                // 현재 유입된 작업을 버려 기존 큐 처리 완결성을 우선
                fanoutMetrics.recordDroppedNewest();
                log.debug("Fan-out queue full. Dropping newest message. policy={}", backpressurePolicy, rejectedExecutionException);
            }
            case DISCONNECT_SLOW_CONSUMER -> {
                // 느린 소비자 세션 일부를 강제 종료해 전체 큐 회복을 유도
                int closed = disconnectSessionsForBackpressure(Math.max(1, backpressureDisconnectCount), backpressureDisconnectReason);
                fanoutMetrics.recordDroppedDisconnectPolicy(closed);
                log.debug(
                        "Fan-out queue full. Applied disconnect policy. disconnectedSessions={}, reason={}",
                        closed,
                        backpressureDisconnectReason
                );
            }
        }
    }

    private int disconnectSessionsForBackpressure(int target, String reason) {
        int closed = 0;
        for (WebSocketSessionRegistry.SessionSnapshot snapshot : webSocketSessionRegistry.snapshots()) {
            if (closed >= target) {
                break;
            }
            boolean sessionClosed = webSocketSessionRegistry.closeSession(snapshot.sessionId(), reason);
            if (sessionClosed) {
                closed++;
            }
        }
        return closed;
    }

    private ThreadPoolExecutor buildExecutor(
            String threadNamePrefix,
            int corePoolSize,
            int maxPoolSize,
            int queueCapacity
    ) {
        // 잘못된 설정값(0/음수)로 실행기가 깨지지 않도록 방어적으로 보정
        int safeCorePoolSize = Math.max(corePoolSize, 1);
        int safeMaxPoolSize = Math.max(maxPoolSize, safeCorePoolSize);
        int safeQueueCapacity = Math.max(queueCapacity, 1);
        return new ThreadPoolExecutor(
                safeCorePoolSize,
                safeMaxPoolSize,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(safeQueueCapacity),
                new ThreadFactory() {
                    private final AtomicInteger sequence = new AtomicInteger();

                    @Override
                    public Thread newThread(Runnable runnable) {
                        Thread thread = new Thread(runnable, threadNamePrefix + sequence.incrementAndGet());
                        thread.setDaemon(true);
                        return thread;
                    }
                },
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    @PreDestroy
    public void shutdownExecutors() {
        // 종료 직전 배치 버퍼를 flush해 프로세스 종료 시점 유실을 줄인다.
        for (Map.Entry<String, TalkBatchBuffer> entry : talkBatchBuffers.entrySet()) {
            String roomId = entry.getKey();
            TalkBatchBuffer buffer = entry.getValue();
            List<ChatMessage> remaining;
            synchronized (buffer) {
                remaining = drainBufferLocked(buffer);
                if (buffer.messages.isEmpty() && buffer.scheduledFlush == null) {
                    talkBatchBuffers.remove(roomId, buffer);
                }
            }
            if (!remaining.isEmpty()) {
                submitTalkBatch(roomId, remaining);
            }
        }
        shutdownGracefully(talkBatchScheduler, "fanout-talk-batch");
        shutdownGracefully(talkFanoutExecutor, "fanout-talk");
        shutdownGracefully(systemFanoutExecutor, "fanout-system");
    }

    private enum BackpressurePolicy {
        DROP_OLDEST,
        DROP_NEWEST,
        DISCONNECT_SLOW_CONSUMER;

        private static BackpressurePolicy from(String value) {
            if (value == null || value.isBlank()) {
                return DROP_NEWEST;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "drop-oldest" -> DROP_OLDEST;
                case "disconnect-slow-consumer" -> DISCONNECT_SLOW_CONSUMER;
                default -> DROP_NEWEST;
            };
        }
    }

    private static class TalkBatchBuffer {
        private final List<ChatMessage> messages = new ArrayList<>();
        private ScheduledFuture<?> scheduledFlush;
    }

    private void shutdownGracefully(ExecutorService executor, String executorName) {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                List<Runnable> dropped = executor.shutdownNow();
                log.warn("Force shutdown '{}' executor. droppedTasks={}", executorName, dropped.size());
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            List<Runnable> dropped = executor.shutdownNow();
            log.warn(
                    "Interrupted while shutting down '{}' executor. droppedTasks={}",
                    executorName,
                    dropped.size()
            );
        }
    }

}
