package com.example.demo.service;

import com.example.demo.model.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

@Slf4j
@Service
public class LongPollingService {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);
    private static final int MAX_BUFFER_SIZE = 100; // 최대 버퍼 크기
    private static final long MESSAGE_RETENTION_MS = 30000; // 30초 동안 메시지 보관

    private final ConcurrentHashMap<String, Set<DeferredResult<ChatMessage>>> waiters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<ChatMessage>> messageHistory = new ConcurrentHashMap<>();

    public DeferredResult<ChatMessage> createWaiter(String roomId) {
        return createWaiter(roomId, null, DEFAULT_TIMEOUT);
    }

    public DeferredResult<ChatMessage> createWaiter(String roomId, Long lastMessageTime) {
        return createWaiter(roomId, lastMessageTime, DEFAULT_TIMEOUT);
    }

    public DeferredResult<ChatMessage> createWaiter(String roomId, Long lastMessageTime, Duration timeout) {
        // 먼저 lastMessageTime 이후의 메시지가 있는지 확인
        List<ChatMessage> history = messageHistory.get(roomId);
        if (history != null && !history.isEmpty()) {
            // lastMessageTime 이후의 첫 번째 메시지 찾기
            ChatMessage nextMessage = history.stream()
                    .filter(msg -> lastMessageTime == null || msg.getTimestamp() > lastMessageTime)
                    .findFirst()
                    .orElse(null);

            if (nextMessage != null) {
                log.info(
                        "[LONG_POLL] Immediate response from history - RoomId: {}, History size: {}, Sender: {}, Message: {}, Timestamp: {}",
                        roomId, history.size(), nextMessage.getSender(), nextMessage.getMessage(),
                        nextMessage.getTimestamp());
                DeferredResult<ChatMessage> deferred = new DeferredResult<>(timeout.toMillis());
                deferred.setResult(nextMessage);
                return deferred;
            }
        }

        // 대기 중인 메시지가 없으면 waiter 생성
        DeferredResult<ChatMessage> deferred = new DeferredResult<>(timeout.toMillis());
        Set<DeferredResult<ChatMessage>> roomWaiters = waiters.computeIfAbsent(roomId,
                k -> ConcurrentHashMap.newKeySet());
        roomWaiters.add(deferred);

        log.info("[LONG_POLL] Waiter created - RoomId: {}, Total waiters: {}, Timeout: {}ms",
                roomId, roomWaiters.size(), timeout.toMillis());

        deferred.onCompletion(() -> {
            roomWaiters.remove(deferred);
            log.debug("[LONG_POLL] Waiter completed - RoomId: {}, Remaining waiters: {}",
                    roomId, roomWaiters.size());
        });

        deferred.onTimeout(() -> {
            roomWaiters.remove(deferred);
            log.info("[LONG_POLL] Waiter timeout - RoomId: {}, Remaining waiters: {}",
                    roomId, roomWaiters.size());
        });

        deferred.onError((e) -> {
            roomWaiters.remove(deferred);
            log.error("[LONG_POLL] Waiter error - RoomId: {}, Error: {}, Remaining waiters: {}",
                    roomId, e.getMessage(), roomWaiters.size());
        });

        return deferred;
    }

    public void broadcast(String roomId, ChatMessage message) {
        // 메시지에 타임스탬프 설정 (아직 없다면)
        if (message.getTimestamp() == null) {
            message.setTimestamp(System.currentTimeMillis());
        }

        // 메시지 이력에 추가
        List<ChatMessage> history = messageHistory.computeIfAbsent(roomId, k -> new ArrayList<>());
        synchronized (history) {
            history.add(message);

            // 오래된 메시지 정리 (30초 이상 지난 메시지)
            long cutoffTime = System.currentTimeMillis() - MESSAGE_RETENTION_MS;
            history.removeIf(msg -> msg.getTimestamp() < cutoffTime);

            // 최대 크기 제한
            while (history.size() > MAX_BUFFER_SIZE) {
                ChatMessage removed = history.remove(0);
                log.warn("[LONG_POLL] History overflow - Removed oldest message from RoomId: {}, Message: {}",
                        roomId, removed.getMessage());
            }
        }

        Set<DeferredResult<ChatMessage>> roomWaiters = waiters.get(roomId);

        // 대기 중인 waiter가 있으면 메시지 전달
        if (roomWaiters != null && !roomWaiters.isEmpty()) {
            int waiterCount = roomWaiters.size();
            log.info(
                    "[LONG_POLL] Broadcasting message - RoomId: {}, Waiters: {}, Sender: {}, Message: {}, Timestamp: {}",
                    roomId, waiterCount, message.getSender(), message.getMessage(), message.getTimestamp());

            int successCount = 0;
            for (DeferredResult<ChatMessage> waiter : roomWaiters) {
                try {
                    waiter.setResult(message);
                    successCount++;
                } catch (Exception e) {
                    log.error("[LONG_POLL] Failed to send message to waiter - RoomId: {}, Error: {}",
                            roomId, e.getMessage());
                }
            }

            roomWaiters.clear();
            log.info("[LONG_POLL] Broadcast complete - RoomId: {}, Success: {}/{}",
                    roomId, successCount, waiterCount);
        } else {
            log.info(
                    "[LONG_POLL] Message saved to history - RoomId: {}, History size: {}, Sender: {}, Message: {}, Timestamp: {}",
                    roomId, history.size(), message.getSender(), message.getMessage(), message.getTimestamp());
        }
    }
}
