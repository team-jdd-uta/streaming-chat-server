package com.example.demo.service;

import com.example.demo.config.WebSocketLifecycleProperties;
import com.example.demo.service.WebSocketSessionRegistry.SessionSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketLifecycleScheduler {

    private final WebSocketLifecycleProperties lifecycleProperties;
    private final WebSocketDrainService drainService;
    private final WebSocketSessionRegistry sessionRegistry;
    private final WebSocketControlService controlService;

    @Scheduled(fixedDelayString = "${chat.ws.lifecycle.scheduler-interval:5s}")
    public void manageConnections() {
        Instant now = Instant.now();

        for (SessionSnapshot session : sessionRegistry.snapshots()) {
            Instant noticeAt = session.expiresAt().minus(lifecycleProperties.getTtlNoticeBefore());
            if (!session.ttlNoticeSent() && !noticeAt.isAfter(now)) {
                if (sessionRegistry.markTtlNoticeSent(session.sessionId())) {
                    controlService.broadcastReconnectSignal("TTL rotation");
                }
            }
        }

        int ttlClosed = sessionRegistry.closeExpired(now);
        if (ttlClosed > 0) {
            log.info("Closed {} expired websocket sessions by TTL", ttlClosed);
        }

        if (!drainService.isDraining()) {
            return;
        }

        controlService.broadcastReconnectSignal("Server draining");

        drainService.getDrainStartedAt().ifPresent(startedAt -> {
            Instant forceCloseAt = startedAt.plus(lifecycleProperties.getDrainForceCloseAfter());
            if (!forceCloseAt.isAfter(now)) {
                int closed = sessionRegistry.closeAll("Draining force close");
                if (closed > 0) {
                    log.info("Closed {} websocket sessions during drain force-close window", closed);
                }
            }
        });
    }
}
