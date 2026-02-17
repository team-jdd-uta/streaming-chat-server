package com.example.demo.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
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
    private static final int INVALID_PAYLOAD_CLOSE_CODE = 4002;

    private final ChatRoomSessionTracker chatRoomSessionTracker;
    private final ObjectProvider<ChatRoomService> chatRoomServiceProvider;
    private final Map<String, SessionMeta> sessions = new ConcurrentHashMap<>();

    /**
     * 생성자에서 ChatRoomService를 ObjectProvider로 받는 이유:
     * - WebSocketSessionRegistry -> ChatRoomService 직접 주입 시 순환 의존이 생길 수 있어
     *   실제 사용 시점(unregister)까지 조회를 지연한다.
     */
    public WebSocketSessionRegistry(
            ChatRoomSessionTracker chatRoomSessionTracker,
            ObjectProvider<ChatRoomService> chatRoomServiceProvider
    ) {
        this.chatRoomSessionTracker = chatRoomSessionTracker;
        this.chatRoomServiceProvider = chatRoomServiceProvider;
    }

    /**
     * websocket 연결 직후 세션을 registry에 등록한다.
     * expiresAt은 TTL 스케줄러가 만료 세션을 닫을 때 기준으로 사용된다.
     */
    public void register(WebSocketSession session, Instant expiresAt) {
        sessions.put(session.getId(), new SessionMeta(session, expiresAt));
    }

    /**
     * websocket 연결 종료 시 호출된다.
     * - registry에서 세션 제거
     * - session이 참여하던 room 목록을 tracker에서 회수
     * - 각 room에 대해 leaveChatRoom을 호출해 refcount 누수를 방지
     *
     * 핵심 목적:
     * - QUIT 메시지 없이 종료되는 비정상 케이스(브라우저 새로고침/강제 종료)에서도
     *   room별 refcount가 감소되도록 보장한다.
     */
    public void unregister(String sessionId) {
        if (sessionId == null || sessionId.isBlank()){
            return;
        }
        sessions.remove(sessionId);
        ChatRoomService chatRoomService = chatRoomServiceProvider.getIfAvailable();
        // 변경: 비정상 종료(새로고침/브라우저 종료)에서도 room별 leave를 보장해 refcount 누수를 방지한다.
        String roomId = chatRoomSessionTracker.unregisterSession(sessionId);
        if (chatRoomService != null && roomId != null) {
            chatRoomService.leaveChatRoom(roomId);
        }
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

    public boolean closeSession(String sessionId, String reason) {
        SessionMeta meta = sessions.get(sessionId);
        if (meta == null) {
            return false;
        }
        // CloseStatus reason 길이 제한을 고려해 짧은 기본 사유를 사용한다.
        String closeReason = (reason == null || reason.isBlank()) ? "Invalid payload" : reason;
        return close(meta.session, new CloseStatus(INVALID_PAYLOAD_CLOSE_CODE, closeReason));
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
