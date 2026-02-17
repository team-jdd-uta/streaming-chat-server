package com.example.demo.monitoring.service;

import com.example.demo.model.ChatMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@Component
public class WebSocketFanoutMetrics {

    // fan-out convertAndSend 지연을 버킷으로 집계해 P95/P99를 근사 계산한다.
    private static final long[] LATENCY_BUCKET_UPPER_BOUNDS_MS = {
            1, 5, 10, 25, 50, 100, 250, 500, 1_000, 2_500, 5_000, 10_000, 30_000, Long.MAX_VALUE
    };

    private final LongAdder redisMessagesReceived = new LongAdder();
    private final LongAdder redisDeserializeErrors = new LongAdder();
    private final LongAdder talkMessagesReceived = new LongAdder();
    private final LongAdder systemMessagesReceived = new LongAdder();
    private final LongAdder fanoutEnqueuedTalk = new LongAdder();
    private final LongAdder fanoutEnqueuedSystem = new LongAdder();
    private final LongAdder fanoutDroppedNewest = new LongAdder();
    private final LongAdder fanoutDroppedOldest = new LongAdder();
    private final LongAdder fanoutDroppedDisconnectPolicy = new LongAdder();
    private final LongAdder backpressureDisconnectCount = new LongAdder();
    private final LongAdder fanoutConvertSuccess = new LongAdder();
    private final LongAdder fanoutConvertError = new LongAdder();

    private final AtomicLong talkQueueDepth = new AtomicLong();
    private final AtomicLong talkQueueDepthMax = new AtomicLong();
    private final AtomicLong systemQueueDepth = new AtomicLong();
    private final AtomicLong systemQueueDepthMax = new AtomicLong();

    private final LongAdder[] fanoutLatencyBuckets = createLatencyBuckets();
    private final AtomicLong fanoutLatencyMaxMs = new AtomicLong();
    private final AtomicLong fanoutLatencyMinMs = new AtomicLong(Long.MAX_VALUE);
    private final LongAdder fanoutLatencySumMs = new LongAdder();
    private final LongAdder fanoutLatencyCount = new LongAdder();

    // close status/에러 타입은 key 기반 카운터로 모아 원인별 추이를 비교한다.
    private final ConcurrentHashMap<String, LongAdder> closeStatusCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LongAdder> fanoutErrorTypeCounts = new ConcurrentHashMap<>();

    private final LongAdder outboundChannelHandled = new LongAdder();
    private final LongAdder outboundChannelRejected = new LongAdder();
    private final LongAdder outboundChannelFailed = new LongAdder();
    private final LongAdder outboundChannelLatencyCount = new LongAdder();
    private final LongAdder outboundChannelLatencySumMs = new LongAdder();
    private final AtomicLong outboundChannelLatencyMaxMs = new AtomicLong();
    private final AtomicLong outboundChannelInFlight = new AtomicLong();
    private final AtomicLong outboundChannelInFlightMax = new AtomicLong();
    private final AtomicLong outboundChannelQueueDepth = new AtomicLong();
    private final AtomicLong outboundChannelQueueDepthMax = new AtomicLong();


    public void recordRedisMessageReceived(ChatMessage message) {
        redisMessagesReceived.increment();
        if (message == null || message.getType() == null) {
            return;
        }
        if (ChatMessage.MessageType.TALK.equals(message.getType())) {
            talkMessagesReceived.increment();
            return;
        }
        systemMessagesReceived.increment();
    }

    public void recordRedisDeserializeError() {
        redisDeserializeErrors.increment();
    }

    public void recordFanoutEnqueued(boolean talkMessage, int queueDepth) {
        if (talkMessage) {
            fanoutEnqueuedTalk.increment();
            updateQueueDepth(talkQueueDepth, talkQueueDepthMax, queueDepth);
            return;
        }
        fanoutEnqueuedSystem.increment();
        updateQueueDepth(systemQueueDepth, systemQueueDepthMax, queueDepth);
    }

    public void recordDroppedNewest() {
        fanoutDroppedNewest.increment();
    }

    public void recordDroppedOldest() {
        fanoutDroppedOldest.increment();
    }

    public void recordDroppedDisconnectPolicy(int disconnectedSessions) {
        fanoutDroppedDisconnectPolicy.increment();
        if (disconnectedSessions > 0) {
            backpressureDisconnectCount.add(disconnectedSessions);
        }
    }

    public void recordFanoutConvertResult(long durationNanos, Throwable throwable) {
        long durationMs = Math.max(0L, durationNanos / 1_000_000L);
        fanoutLatencyCount.increment();
        fanoutLatencySumMs.add(durationMs);
        updateMin(fanoutLatencyMinMs, durationMs);
        updateMax(fanoutLatencyMaxMs, durationMs);
        fanoutLatencyBuckets[bucketIndex(durationMs)].increment();

        if (throwable == null) {
            fanoutConvertSuccess.increment();
            return;
        }

        fanoutConvertError.increment();
        fanoutErrorTypeCounts.computeIfAbsent(throwable.getClass().getSimpleName(), ignored -> new LongAdder()).increment();
    }

    public void outboundChannelMessageStarted() {
        // 동시에 처리 중인 outbound 메시지 수를 추적해 순간 버스트를 확인한다.
        long inFlight = outboundChannelInFlight.incrementAndGet();
        updateMax(outboundChannelInFlightMax, inFlight);
    }

    public void outboundChannelMessageCompleted(long durationNanos) {
        long durationMs = Math.max(0L, durationNanos / 1_000_000L);
        outboundChannelLatencyCount.increment();
        outboundChannelLatencySumMs.add(durationMs);
        updateMax(outboundChannelLatencyMaxMs, durationMs);
        outboundChannelHandled.increment();
        long remaining = outboundChannelInFlight.decrementAndGet();
        if (remaining < 0) {
            outboundChannelInFlight.set(0);
        }
    }

    public void recordOutboundChannelRejected() {
        outboundChannelRejected.increment();
    }

    public void recordOutboundChannelFailed() {
        outboundChannelFailed.increment();
    }

    public void recordOutboundChannelQueueDepth(int queueDepth) {
        long depth = Math.max(queueDepth, 0);
        outboundChannelQueueDepth.set(depth);
        updateMax(outboundChannelQueueDepthMax, depth);
    }

    public void recordCloseStatus(CloseStatus closeStatus) {
        // 예: "1011:keepalive ping timeout" 형태로 코드+사유를 함께 보존
        String key = closeStatus == null ? "unknown" : closeStatus.getCode() + ":" + safeReason(closeStatus.getReason());
        closeStatusCounts.computeIfAbsent(key, ignored -> new LongAdder()).increment();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("redisMessagesReceived", redisMessagesReceived.sum());
        payload.put("redisDeserializeErrors", redisDeserializeErrors.sum());
        payload.put("talkMessagesReceived", talkMessagesReceived.sum());
        payload.put("systemMessagesReceived", systemMessagesReceived.sum());
        payload.put("fanoutEnqueuedTalk", fanoutEnqueuedTalk.sum());
        payload.put("fanoutEnqueuedSystem", fanoutEnqueuedSystem.sum());
        payload.put("fanoutDroppedNewest", fanoutDroppedNewest.sum());
        payload.put("fanoutDroppedOldest", fanoutDroppedOldest.sum());
        payload.put("fanoutDroppedDisconnectPolicy", fanoutDroppedDisconnectPolicy.sum());
        payload.put("backpressureDisconnectCount", backpressureDisconnectCount.sum());
        payload.put("fanoutConvertSuccess", fanoutConvertSuccess.sum());
        payload.put("fanoutConvertError", fanoutConvertError.sum());
        payload.put("talkQueueDepth", talkQueueDepth.get());
        payload.put("talkQueueDepthMax", talkQueueDepthMax.get());
        payload.put("systemQueueDepth", systemQueueDepth.get());
        payload.put("systemQueueDepthMax", systemQueueDepthMax.get());
        payload.put("fanoutLatencyMs", latencySnapshot());
        payload.put("closeStatusCounts", toSimpleCountMap(closeStatusCounts));
        payload.put("fanoutErrorTypeCounts", toSimpleCountMap(fanoutErrorTypeCounts));
        payload.put("outboundChannel", outboundChannelSnapshot());
        return payload;
    }

    private Map<String, Object> outboundChannelSnapshot() {
        long count = outboundChannelLatencyCount.sum();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("handled", outboundChannelHandled.sum());
        payload.put("rejected", outboundChannelRejected.sum());
        payload.put("failed", outboundChannelFailed.sum());
        payload.put("inFlight", outboundChannelInFlight.get());
        payload.put("inFlightMax", outboundChannelInFlightMax.get());
        payload.put("queueDepth", outboundChannelQueueDepth.get());
        payload.put("queueDepthMax", outboundChannelQueueDepthMax.get());
        payload.put("latencyMs", Map.of(
                "count", count,
                "avgMs", count == 0 ? 0.0 : ((double) outboundChannelLatencySumMs.sum()) / count,
                "maxMs", outboundChannelLatencyMaxMs.get()
        ));
        return payload;
    }

    private int bucketIndex(long durationMs) {
        for (int i = 0; i < LATENCY_BUCKET_UPPER_BOUNDS_MS.length; i++) {
            if (durationMs <= LATENCY_BUCKET_UPPER_BOUNDS_MS[i]) {
                return i;
            }
        }
        return LATENCY_BUCKET_UPPER_BOUNDS_MS.length - 1;
    }

    private Map<String, Object> latencySnapshot() {
        long count = fanoutLatencyCount.sum();
        Map<String, Object> latency = new LinkedHashMap<>();
        latency.put("count", count);
        latency.put("minMs", count == 0 ? 0L : fanoutLatencyMinMs.get());
        latency.put("maxMs", fanoutLatencyMaxMs.get());
        latency.put("avgMs", count == 0 ? 0.0 : ((double) fanoutLatencySumMs.sum()) / count);
        latency.put("p95Ms", estimatePercentile(0.95));
        latency.put("p99Ms", estimatePercentile(0.99));
        return latency;
    }

    private long estimatePercentile(double percentile) {
        long count = fanoutLatencyCount.sum();
        if (count == 0) {
            return 0L;
        }
        // 히스토그램 누적값으로 분위수를 근사한다(정밀 샘플 저장 없이 저비용 계산).
        long threshold = Math.max(1L, (long) Math.ceil(count * percentile));
        long cumulative = 0L;
        for (int i = 0; i < fanoutLatencyBuckets.length; i++) {
            cumulative += fanoutLatencyBuckets[i].sum();
            if (cumulative >= threshold) {
                return LATENCY_BUCKET_UPPER_BOUNDS_MS[i] == Long.MAX_VALUE
                        ? fanoutLatencyMaxMs.get()
                        : LATENCY_BUCKET_UPPER_BOUNDS_MS[i];
            }
        }
        return fanoutLatencyMaxMs.get();
    }

    private void updateQueueDepth(AtomicLong current, AtomicLong max, int depth) {
        long normalized = Math.max(depth, 0);
        current.set(normalized);
        updateMax(max, normalized);
    }

    private void updateMax(AtomicLong max, long value) {
        long prev;
        do {
            prev = max.get();
            if (value <= prev) {
                return;
            }
        } while (!max.compareAndSet(prev, value));
    }

    private void updateMin(AtomicLong min, long value) {
        long prev;
        do {
            prev = min.get();
            if (value >= prev) {
                return;
            }
        } while (!min.compareAndSet(prev, value));
    }

    private LongAdder[] createLatencyBuckets() {
        LongAdder[] buckets = new LongAdder[LATENCY_BUCKET_UPPER_BOUNDS_MS.length];
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = new LongAdder();
        }
        return buckets;
    }

    private Map<String, Long> toSimpleCountMap(ConcurrentHashMap<String, LongAdder> source) {
        Map<String, Long> map = new LinkedHashMap<>();
        source.forEach((key, value) -> map.put(key, value.sum()));
        return map;
    }

    private String safeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "-";
        }
        return reason;
    }
}
