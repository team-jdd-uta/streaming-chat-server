package com.example.demo.monitoring.config;

import com.example.demo.monitoring.service.WebSocketFanoutMetrics;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.concurrent.RejectedExecutionException;

@Component
public class OutboundChannelMetricsInterceptor implements ChannelInterceptor {

    // Message 헤더에 시작 시각을 심어 afterSendCompletion에서 처리시간을 계산한다.
    private static final String START_NANOS_HEADER = "x-outbound-start-nanos";
    private final WebSocketFanoutMetrics fanoutMetrics;

    public OutboundChannelMetricsInterceptor(WebSocketFanoutMetrics fanoutMetrics) {
        this.fanoutMetrics = fanoutMetrics;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        fanoutMetrics.outboundChannelMessageStarted();
        // 기존 메시지를 복사해 계측용 헤더만 추가(원 payload 불변 유지)
        return MessageBuilder.fromMessage(message)
                .setHeader(START_NANOS_HEADER, System.nanoTime())
                .build();
    }

    @Override
    public void afterSendCompletion(Message<?> message, MessageChannel channel, boolean sent, Exception ex) {
        Object start = message.getHeaders().get(START_NANOS_HEADER);
        // 헤더 누락 시 현재 시각으로 보정해 음수 지연/예외 전파를 방지
        long startedAt = (start instanceof Number number) ? number.longValue() : System.nanoTime();
        fanoutMetrics.outboundChannelMessageCompleted(System.nanoTime() - startedAt);
        if (ex != null) {
            if (isRejected(ex)) {
                fanoutMetrics.recordOutboundChannelRejected();
            } else {
                fanoutMetrics.recordOutboundChannelFailed();
            }
            return;
        }
        if (!sent) {
            fanoutMetrics.recordOutboundChannelFailed();
        }
    }

    private boolean isRejected(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof RejectedExecutionException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
