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
        int ttlNoticeMarkedCount = 0;

        for (SessionSnapshot session : sessionRegistry.snapshots()) {
            Instant noticeAt = session.expiresAt().minus(lifecycleProperties.getTtlNoticeBefore());
            if (!session.ttlNoticeSent() && !noticeAt.isAfter(now)) {
                if (sessionRegistry.markTtlNoticeSent(session.sessionId())) {
                    ttlNoticeMarkedCount++;
                }
            }
        }
        // 변경: TTL 임박 세션이 여러 개여도 주기마다 제어 메시지는 1회만 전송
        // 이유: 세션 수 증가 시 동일 RECONNECT 브로드캐스트가 과도하게 반복되는 문제를 줄이기 위해
        if (ttlNoticeMarkedCount > 0) {
            controlService.broadcastReconnectSignal("TTL rotation");
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
