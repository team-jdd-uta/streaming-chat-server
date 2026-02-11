package com.example.demo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WsControlMessage {
    private String type;
    private String reason;
    private int retryAfterMs;
    private int reconnectJitterMaxMs;
    private String serverId;
}
