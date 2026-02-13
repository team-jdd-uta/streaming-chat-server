package com.example.demo.controller;

import com.example.demo.service.WebSocketControlService;
import com.example.demo.service.WebSocketDrainService;
import com.example.demo.service.WebSocketSessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/ops/ws")
@RequiredArgsConstructor
public class WebSocketOpsController {

    private final WebSocketDrainService drainService;
    private final WebSocketSessionRegistry sessionRegistry;
    private final WebSocketControlService controlService;

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
}
