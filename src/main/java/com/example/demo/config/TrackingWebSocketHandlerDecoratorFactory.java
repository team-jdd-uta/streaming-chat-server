package com.example.demo.config;

import com.example.demo.service.WebSocketSessionRegistry;
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

    @Override
    public WebSocketHandler decorate(WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                Instant expiresAt = Instant.now().plus(lifecycleProperties.getTtl());
                sessionRegistry.register(session, expiresAt);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
                sessionRegistry.unregister(session.getId());
                super.afterConnectionClosed(session, closeStatus);
            }
        };
    }
}
