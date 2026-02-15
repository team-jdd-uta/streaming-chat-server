package com.example.demo.service.impl;

import com.example.demo.model.ChatMessage;
import com.example.demo.service.MessageBrokerService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class RedisMessageBrokerService implements MessageBrokerService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate streamStringRedisTemplate;
    // Stream(XADD) 저장 전용 실행기
    // 의도: Pub/Sub 실시간 전달 경로와 영속 저장 경로를 분리해 서로의 지연 영향을 줄인다.
    private final ExecutorService streamAppendExecutor = new ThreadPoolExecutor(
            2,
            2,
            0L,
            TimeUnit.MILLISECONDS,
            // fan-out 버스트 시 무한 큐 적체를 막기 위해 bounded queue 사용
            new ArrayBlockingQueue<>(20_000),
            new ThreadFactory() {
                private final AtomicInteger seq = new AtomicInteger();

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "chat-stream-append-" + seq.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }
            },
            // 큐가 가득 차면 호출 스레드가 일부 부담해 자연스럽게 입력 속도를 늦춘다.
            new ThreadPoolExecutor.CallerRunsPolicy()
    );
    // Pub/Sub 재시도 전용 실행기
    // 의도: failover 구간 재시도를 분리해 메인 요청 스레드 점유를 최소화한다.
    private final ExecutorService pubSubRetryExecutor = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(2_000),
            new ThreadFactory() {
                private final AtomicInteger seq = new AtomicInteger();

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "chat-pubsub-retry-" + seq.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @Value("${chat.stream.key-prefix:chat:stream:room:}")
    // room별 Stream 키 네이밍 통일용 접두사
    private String streamKeyPrefix;
    @Value("${chat.pubsub.publish.retry.max-attempts:3}")
    // Pub/Sub 실패 시 최대 재시도 횟수
    private int pubSubRetryMaxAttempts;
    @Value("${chat.pubsub.publish.retry.backoff-ms:200}")
    // Pub/Sub 재시도 간 backoff(ms)
    private long pubSubRetryBackoffMs;

    public RedisMessageBrokerService(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("streamStringRedisTemplate") StringRedisTemplate streamStringRedisTemplate
    ) {
        this.redisTemplate = redisTemplate;
        this.streamStringRedisTemplate = streamStringRedisTemplate;
    }

    @Override
    public void publish(String topic, Object message) {
        // 실시간 fan-out과 무관하게 저장 시도는 항상 진행되도록 먼저 enqueue
        enqueueStreamAppend(topic, message);
        try {
            // 정상 경로는 즉시 1회 publish (지연 최소화)
            redisTemplate.convertAndSend(topic, message);
        } catch (Exception e) {
            // failover 등 일시 장애는 비동기 재시도로 흡수
            log.warn("Failed to publish message to Redis Pub/Sub on first attempt. topic={}", topic, e);
            enqueuePubSubRetry(topic, message);
        }
    }

    private void enqueueStreamAppend(String topic, Object message) {
        // XADD 경로를 비동기화해 publish 지연에 직접 전이되지 않게 한다.
        streamAppendExecutor.execute(() -> {
            try {
                appendToStream(topic, message);
            } catch (Exception e) {
                log.warn("Failed to append chat message to stream. topic={}", topic, e);
            }
        });
    }

    private void enqueuePubSubRetry(String topic, Object message) {
        pubSubRetryExecutor.execute(() -> retryPublish(topic, message));
    }

    private void retryPublish(String topic, Object message) {
        int attempts = Math.max(pubSubRetryMaxAttempts, 0);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                TimeUnit.MILLISECONDS.sleep(pubSubRetryBackoffMs);
                redisTemplate.convertAndSend(topic, message);
                log.info("Recovered Redis Pub/Sub publish after retry. topic={}, attempt={}", topic, attempt);
                return;
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                log.warn("Pub/Sub retry interrupted. topic={}", topic, interruptedException);
                return;
            } catch (Exception e) {
                if (attempt == attempts) {
                    log.error("Failed Redis Pub/Sub publish after retries. topic={}, attempts={}", topic, attempts, e);
                    return;
                }
                log.warn("Redis Pub/Sub retry failed. topic={}, attempt={}", topic, attempt, e);
            }
        }
    }

    private void appendToStream(String topic, Object message) {
        // topic(room) 단위 키 분리로 방별 이력 추적성을 높인다.
        String streamKey = streamKeyPrefix + topic;
        Map<String, String> fields = new LinkedHashMap<>();

        if (message instanceof ChatMessage chatMessage) {
            fields.put("type", chatMessage.getType() == null ? "" : chatMessage.getType().name());
            fields.put("roomId", safe(chatMessage.getRoomId()));
            fields.put("sender", safe(chatMessage.getSender()));
            // msgId를 함께 저장해 테스트/운영 시 메시지 유실 추적 근거로 사용한다.
            fields.put("msgId", safe(chatMessage.getMsgId()));
            fields.put("message", safe(chatMessage.getMessage()));
        } else {
            fields.put("payload", String.valueOf(message));
        }
        fields.put("publishedAt", String.valueOf(System.currentTimeMillis()));

        MapRecord<String, String, String> record = StreamRecords.mapBacked(fields).withStreamKey(streamKey);
        // Pub/Sub는 휘발성이므로, Stream(XADD)으로 재조회 가능한 로그를 남긴다.
        streamStringRedisTemplate.opsForStream().add(record);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @PreDestroy
    public void shutdownExecutor() {
        streamAppendExecutor.shutdown();
        pubSubRetryExecutor.shutdown();
    }
}
