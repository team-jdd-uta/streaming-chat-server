package com.example.demo.controller;

import com.example.demo.monitoring.service.WebSocketFanoutMetrics;
import com.example.demo.service.WebSocketControlService;
import com.example.demo.service.WebSocketDrainService;
import com.example.demo.service.WebSocketSessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.socket.config.WebSocketMessageBrokerStats;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/ops/ws")
@RequiredArgsConstructor
public class WebSocketOpsController {

    private final WebSocketDrainService drainService;
    private final WebSocketSessionRegistry sessionRegistry;
    private final WebSocketControlService controlService;
    private final WebSocketFanoutMetrics fanoutMetrics;
    private final WebSocketMessageBrokerStats webSocketMessageBrokerStats;

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("draining", drainService.isDraining());
        response.put("drainStartedAt", drainService.getDrainStartedAt().map(Instant::toString).orElse(null));
        response.put("activeSessions", sessionRegistry.size());
        return response;
    }

    @PostMapping("/drain")
    public ResponseEntity<Map<String, Object>> setDrain(@RequestParam("enabled") boolean enabled) {
        drainService.setDraining(enabled);
        if (enabled) {
            controlService.broadcastReconnectSignal("Server draining");
        }
        return ResponseEntity.ok(status());
    }

    @PostMapping("/disconnect-all")
    public Map<String, Object> disconnectAll(@RequestParam(defaultValue = "Manual disconnect") String reason) {
        int disconnected = sessionRegistry.closeAll(reason);
        return Map.of(
                "disconnected", disconnected,
                "reason", reason
        );
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("draining", drainService.isDraining());
        response.put("activeSessions", sessionRegistry.size());
        // Spring이 문자열로 제공하는 executor 통계를 파싱해 구조화된 JSON으로 노출
        Map<String, Object> outbound = parseExecutorStats(webSocketMessageBrokerStats.getClientOutboundExecutorStatsInfo());
        long queuedTasks = (long) outbound.getOrDefault("queuedTasks", -1L);
        if (queuedTasks >= 0) {
            // outbound queued tasks를 fanout metrics에도 반영해 병목 추세를 단일 지표로 확인
            fanoutMetrics.recordOutboundChannelQueueDepth((int) queuedTasks);
        }
        response.put("fanout", fanoutMetrics.snapshot());
        response.put("clientOutboundExecutor", outbound);
        response.put("clientInboundExecutor", parseExecutorStats(webSocketMessageBrokerStats.getClientInboundExecutorStatsInfo()));
        return response;
    }

    private Map<String, Object> parseExecutorStats(String statsInfo) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("raw", statsInfo);
        // 예: "pool size = 8, active threads = 2, queued tasks = 91, completed tasks = ..."
        // 문자열 포맷이 바뀌면 -1로 떨어지므로, 대시보드에서 포맷 변화 감지도 가능하다.
        payload.put("poolSize", extractNumber(statsInfo, "pool size = (\\d+)"));
        payload.put("activeThreads", extractNumber(statsInfo, "active threads = (\\d+)"));
        payload.put("queuedTasks", extractNumber(statsInfo, "queued tasks = (\\d+)"));
        payload.put("completedTasks", extractNumber(statsInfo, "completed tasks = (\\d+)"));
        return payload;
    }

    private long extractNumber(String text, String regex) {
        if (text == null || text.isBlank()) {
            return -1L;
        }
        Matcher matcher = Pattern.compile(regex).matcher(text);
        if (!matcher.find()) {
            return -1L;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }
}
