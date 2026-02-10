package com.example.demo.service;

import com.example.demo.model.ChatMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LongPollingService {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(25);

    private final ConcurrentHashMap<String, Set<DeferredResult<ChatMessage>>> waiters = new ConcurrentHashMap<>();

    public DeferredResult<ChatMessage> createWaiter(String roomId) {
        return createWaiter(roomId, DEFAULT_TIMEOUT);
    }

    public DeferredResult<ChatMessage> createWaiter(String roomId, Duration timeout) {
        DeferredResult<ChatMessage> deferred = new DeferredResult<>(timeout.toMillis());
        Set<DeferredResult<ChatMessage>> roomWaiters = waiters.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet());
        roomWaiters.add(deferred);

        deferred.onCompletion(() -> roomWaiters.remove(deferred));
        deferred.onTimeout(() -> roomWaiters.remove(deferred));
        deferred.onError((e) -> roomWaiters.remove(deferred));

        return deferred;
    }

    public void broadcast(String roomId, ChatMessage message) {
        Set<DeferredResult<ChatMessage>> roomWaiters = waiters.get(roomId);
        if (roomWaiters == null || roomWaiters.isEmpty()) {
            return;
        }
        for (DeferredResult<ChatMessage> waiter : roomWaiters) {
            waiter.setResult(message);
        }
        roomWaiters.clear();
    }
}
