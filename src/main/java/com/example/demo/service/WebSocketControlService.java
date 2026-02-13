package com.example.demo.service;

import com.example.demo.config.WebSocketLifecycleProperties;
import com.example.demo.model.WsControlMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class WebSocketControlService {

    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketLifecycleProperties lifecycleProperties;
    private final String serverId;

    public WebSocketControlService(
            SimpMessagingTemplate messagingTemplate,
            WebSocketLifecycleProperties lifecycleProperties,
            @Value("${server.port:${local.server.port:8080}}") String serverId
    ) {
        this.messagingTemplate = messagingTemplate;
        this.lifecycleProperties = lifecycleProperties;
        this.serverId = serverId;
    }

    public void broadcastReconnectSignal(String reason) {
        WsControlMessage controlMessage = WsControlMessage.builder()
                .type("RECONNECT")
                .reason(reason)
                .retryAfterMs(lifecycleProperties.getReconnectRetryAfterMs())
                .reconnectJitterMaxMs(lifecycleProperties.getReconnectJitterMaxMs())
                .serverId(serverId)
                .build();
        messagingTemplate.convertAndSend("/sub/system/control", controlMessage);
    }
}
