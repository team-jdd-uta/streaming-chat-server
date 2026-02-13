package com.example.demo.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionRegistry {

    private static final CloseStatus RECONNECT_CLOSE_STATUS = new CloseStatus(4001, "Reconnect required");

    private final Map<String, SessionMeta> sessions = new ConcurrentHashMap<>();

    public void register(WebSocketSession session, Instant expiresAt) {
        sessions.put(session.getId(), new SessionMeta(session, expiresAt));
    }

    public void unregister(String sessionId) {
        sessions.remove(sessionId);
    }

    public int size() {
        return sessions.size();
    }

    public List<SessionSnapshot> snapshots() {
        List<SessionSnapshot> snapshots = new ArrayList<>();
        for (Map.Entry<String, SessionMeta> entry : sessions.entrySet()) {
            SessionMeta meta = entry.getValue();
            snapshots.add(new SessionSnapshot(entry.getKey(), meta.expiresAt, meta.ttlNoticeSent));
        }
        return snapshots;
    }

    public int closeExpired(Instant now) {
        int closed = 0;
        for (SessionMeta meta : sessions.values()) {
            if (!meta.expiresAt.isAfter(now)) {
                if (close(meta.session, RECONNECT_CLOSE_STATUS)) {
                    closed++;
                }
            }
        }
        return closed;
    }

    public int closeAll(String reason) {
        CloseStatus status = new CloseStatus(4001, reason);
        int closed = 0;
        for (SessionMeta meta : sessions.values()) {
            if (close(meta.session, status)) {
                closed++;
            }
        }
        return closed;
    }

    public boolean markTtlNoticeSent(String sessionId) {
        SessionMeta meta = sessions.get(sessionId);
        if (meta == null || meta.ttlNoticeSent) {
            return false;
        }
        meta.ttlNoticeSent = true;
        return true;
    }

    private boolean close(WebSocketSession session, CloseStatus status) {
        if (!session.isOpen()) {
            return false;
        }
        try {
            session.close(status);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    @Getter
    @RequiredArgsConstructor
    private static class SessionMeta {
        private final WebSocketSession session;
        private final Instant expiresAt;
        private volatile boolean ttlNoticeSent;
    }

    public record SessionSnapshot(String sessionId, Instant expiresAt, boolean ttlNoticeSent) {
    }
}
