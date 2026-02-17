package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.ChannelRegistration;
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
    // 전송 계층 튜닝값: 대형 payload/느린 네트워크 상황에서 끊김을 완화하기 위한 설정
    @Value("${chat.ws.transport.message-size-limit:65536}")
    private int messageSizeLimit;
    @Value("${chat.ws.transport.send-buffer-size-limit:1048576}")
    private int sendBufferSizeLimit;
    @Value("${chat.ws.transport.send-time-limit-ms:30000}")
    private int sendTimeLimitMs;
    // STOMP in/out 채널 실행기 튜닝값
    // 목적: fan-out 테스트 중 채널 작업 큐/스레드 병목을 설정으로 제어하기 위함
    @Value("${chat.ws.channel.inbound.core-pool-size:8}")
    private int inboundCorePoolSize;
    @Value("${chat.ws.channel.inbound.max-pool-size:64}")
    private int inboundMaxPoolSize;
    @Value("${chat.ws.channel.inbound.queue-capacity:20000}")
    private int inboundQueueCapacity;
    @Value("${chat.ws.channel.outbound.core-pool-size:8}")
    private int outboundCorePoolSize;
    @Value("${chat.ws.channel.outbound.max-pool-size:64}")
    private int outboundMaxPoolSize;
    @Value("${chat.ws.channel.outbound.queue-capacity:20000}")
    private int outboundQueueCapacity;

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
                .setAllowedOriginPatterns(
                        "http://localhost:*",
                        "http://127.0.0.1:*",
                        "https://*.ngrok-free.app",
                        "https://*.ngrok-free.dev"
                )
                .addInterceptors(drainingHandshakeInterceptor)
                .withSockJS()
                .setSessionCookieNeeded(false);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry
                .addDecoratorFactory(trackingWebSocketHandlerDecoratorFactory)
                // transport 한도는 환경변수로 노출해 실험/운영에서 빠르게 조정 가능하게 한다.
                .setMessageSizeLimit(messageSizeLimit)
                .setSendBufferSizeLimit(sendBufferSizeLimit)
                .setSendTimeLimit(sendTimeLimitMs);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                .corePoolSize(inboundCorePoolSize)
                .maxPoolSize(inboundMaxPoolSize)
                .queueCapacity(inboundQueueCapacity);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                .corePoolSize(outboundCorePoolSize)
                .maxPoolSize(outboundMaxPoolSize)
                .queueCapacity(outboundQueueCapacity);
    }
}
