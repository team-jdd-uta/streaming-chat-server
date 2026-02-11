package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(WebSocketLifecycleProperties.class)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final DrainingHandshakeInterceptor drainingHandshakeInterceptor;
    private final TrackingWebSocketHandlerDecoratorFactory trackingWebSocketHandlerDecoratorFactory;

    public WebSocketConfig(
            DrainingHandshakeInterceptor drainingHandshakeInterceptor,
            TrackingWebSocketHandlerDecoratorFactory trackingWebSocketHandlerDecoratorFactory
    ) {
        this.drainingHandshakeInterceptor = drainingHandshakeInterceptor;
        this.trackingWebSocketHandlerDecoratorFactory = trackingWebSocketHandlerDecoratorFactory;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/sub"); // 메시지를 구독하는 요청(sub)
        registry.setApplicationDestinationPrefixes("/pub"); // 메시지를 발행하는 요청(pub)
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns("*")
                .addInterceptors(drainingHandshakeInterceptor);
        registry.addEndpoint("/ws/chat")
                .setAllowedOriginPatterns("*")
                .addInterceptors(drainingHandshakeInterceptor)
                .withSockJS();
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.addDecoratorFactory(trackingWebSocketHandlerDecoratorFactory);
    }
}
