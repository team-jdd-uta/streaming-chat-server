package com.example.demo.service;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatRoomSessionTracker {

    // 변경: websocket 1개는 1개 room만 소속되도록 session -> room 단일 매핑으로 관리
    private final Map<String, String> sessionToRoom = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> roomToSessions = new ConcurrentHashMap<>();

    /**
     * session을 room에 바인딩한다.
     * - 같은 room으로 중복 ENTER: false
     * - 다른 room으로 ENTER: 기존 room에서 분리 후 새 room으로 이동
     */
    public boolean bindSessionToRoom(String sessionId, String roomId) {
        if (isBlank(sessionId) || isBlank(roomId)) {
            return false;
        }
        String normalizedSessionId = sessionId.trim();
        String normalizedRoomId = roomId.trim();

        String previousRoomId = sessionToRoom.get(normalizedSessionId);
        if (normalizedRoomId.equals(previousRoomId)) {
            return false;
        }

        if (previousRoomId != null) {
            removeFromRoom(previousRoomId, normalizedSessionId);
        }
        sessionToRoom.put(normalizedSessionId, normalizedRoomId);
        roomToSessions.computeIfAbsent(normalizedRoomId, key -> ConcurrentHashMap.newKeySet())
                .add(normalizedSessionId);
        return true;
    }

    public String currentRoomOfSession(String sessionId) {
        if (isBlank(sessionId)) {
            return null;
        }
        return sessionToRoom.get(sessionId.trim());
    }

    // 변경: 중복 QUIT 방지를 위해 실제 해제 여부를 반환(단일 해제 로직 사용)
    public boolean unbindSessionFromRoom(String sessionId, String roomId) {
        return detachSessionInternal(sessionId, roomId) != null;
    }

    // 변경: disconnect 시 단일 room 해제 로직을 재사용하기 위해 roomId를 반환
    public String unregisterSession(String sessionId) {
        return detachSessionInternal(sessionId, null);
    }

    public int countByRoom(String roomId) {
        if (isBlank(roomId)) {
            return 0;
        }
        Set<String> sessions = roomToSessions.get(roomId.trim());
        return sessions == null ? 0 : sessions.size();
    }

    public Map<String, Integer> roomSessionCounts() {
        Map<String, Integer> snapshot = new LinkedHashMap<>();
        roomToSessions.forEach((roomId, sessions) -> snapshot.put(roomId, sessions.size()));
        return snapshot;
    }

    private void removeFromRoom(String roomId, String sessionId) {
        Set<String> sessions = roomToSessions.get(roomId);
        if (sessions == null) {
            return;
        }
        sessions.remove(sessionId);
        if (sessions.isEmpty()) {
            roomToSessions.remove(roomId, sessions);
        }
    }

    /**
     * session 해제의 단일 진입점.
     * - expectedRoomId가 있으면 해당 room에 바인딩된 경우만 해제
     * - expectedRoomId가 null이면 현재 바인딩된 room을 해제
     * 반환값은 실제로 해제된 roomId(없으면 null)
     */
    private String detachSessionInternal(String sessionId, String expectedRoomId) {
        if (isBlank(sessionId)) {
            return null;
        }
        String normalizedSessionId = sessionId.trim();
        String boundRoom = sessionToRoom.get(normalizedSessionId);
        if (boundRoom == null) {
            return null;
        }

        String expected = isBlank(expectedRoomId) ? null : expectedRoomId.trim();
        if (expected != null && !expected.equals(boundRoom)) {
            return null;
        }

        sessionToRoom.remove(normalizedSessionId);
        removeFromRoom(boundRoom, normalizedSessionId);
        return boundRoom;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
