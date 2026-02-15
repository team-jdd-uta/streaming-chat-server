package com.example.demo.config;

import com.example.demo.service.WebSocketSessionRegistry;
import com.example.demo.monitoring.service.WebSocketFanoutMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class TrackingWebSocketHandlerDecoratorFactory implements WebSocketHandlerDecoratorFactory {

    private final WebSocketSessionRegistry sessionRegistry;
    private final WebSocketLifecycleProperties lifecycleProperties;
    private final WebSocketFanoutMetrics fanoutMetrics;

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        // WebSocket lifecycle 훅을 가로채 세션 레지스트리/메트릭을 일관되게 관리한다.
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                // 세션 만료 시각(TTL)을 함께 등록해 drain/강제종료 정책과 연계한다.
                Instant expiresAt = Instant.now().plus(lifecycleProperties.getTtl());
                sessionRegistry.register(session, expiresAt);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                // close code 집계는 fan-out 안정성(1011/1006 등) 원인 분석 핵심 데이터다.
                sessionRegistry.unregister(session.getId());
                fanoutMetrics.recordCloseStatus(closeStatus);
                super.afterConnectionClosed(session, closeStatus);
            }
        };
    }
}
