package com.example.demo.config;

import com.example.demo.service.WebSocketDrainService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class DrainingHandshakeInterceptor implements HandshakeInterceptor {

    private final WebSocketDrainService drainService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!drainService.isDraining()) {
            return true;
        }
        if (response instanceof org.springframework.http.server.ServletServerHttpResponse servletResponse) {
            servletResponse.getServletResponse().setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }
}
