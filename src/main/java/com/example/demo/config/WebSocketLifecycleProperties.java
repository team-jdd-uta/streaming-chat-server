package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "chat.ws.lifecycle")
public class WebSocketLifecycleProperties {

    private Duration ttl = Duration.ofMinutes(15);
    private Duration ttlNoticeBefore = Duration.ofSeconds(30);
    private Duration drainForceCloseAfter = Duration.ofMinutes(2);
    private Duration schedulerInterval = Duration.ofSeconds(5);
    private int reconnectRetryAfterMs = 2_000;
    private int reconnectJitterMaxMs = 10_000;

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public Duration getTtlNoticeBefore() {
        return ttlNoticeBefore;
    }

    public void setTtlNoticeBefore(Duration ttlNoticeBefore) {
        this.ttlNoticeBefore = ttlNoticeBefore;
    }

    public Duration getDrainForceCloseAfter() {
        return drainForceCloseAfter;
    }

    public void setDrainForceCloseAfter(Duration drainForceCloseAfter) {
        this.drainForceCloseAfter = drainForceCloseAfter;
    }

    public Duration getSchedulerInterval() {
        return schedulerInterval;
    }

    public void setSchedulerInterval(Duration schedulerInterval) {
        this.schedulerInterval = schedulerInterval;
    }

    public int getReconnectRetryAfterMs() {
        return reconnectRetryAfterMs;
    }

    public void setReconnectRetryAfterMs(int reconnectRetryAfterMs) {
        this.reconnectRetryAfterMs = reconnectRetryAfterMs;
    }

    public int getReconnectJitterMaxMs() {
        return reconnectJitterMaxMs;
    }

    public void setReconnectJitterMaxMs(int reconnectJitterMaxMs) {
        this.reconnectJitterMaxMs = reconnectJitterMaxMs;
    }
}
