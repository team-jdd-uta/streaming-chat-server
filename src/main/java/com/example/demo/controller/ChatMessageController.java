package com.example.demo.controller;

import com.example.demo.model.ChatMessage;
import com.example.demo.model.ChatRoom;
import com.example.demo.service.ChatRoomSessionTracker;
import com.example.demo.service.ChatRoomService;
import com.example.demo.service.MessageBrokerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

@RequiredArgsConstructor
@Controller
@Slf4j
public class ChatMessageController {

    private final MessageBrokerService messageBrokerService;
    private final ChatRoomService chatRoomService;
    private final ChatRoomSessionTracker chatRoomSessionTracker;

    /**
     * WebSocket "/pub/chat/message"로 들어오는 메시징을 처리
     */
    @MessageMapping("/chat/message")
    public void message(ChatMessage message, SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = extractSessionId(headerAccessor);
        try {
            validateAndNormalizeMessage(message, sessionId);
        } catch (IllegalArgumentException exception) {
            log.warn("Ignored invalid websocket message. sessionId={}, reason={}", sessionId, exception.getMessage());
            return;
        }

        if (ChatMessage.MessageType.ENTER.equals(message.getType())) {
            handleEnter(message, sessionId);
        } else if (ChatMessage.MessageType.QUIT.equals(message.getType())) {
            handleQuit(message, sessionId);
        }
        // 클라이언트로부터 받은 메시지를 Redis로 발행
        messageBrokerService.publish(message.getRoomId(), message);
    }

    private void handleEnter(ChatMessage message, String sessionId) {
        // 지금 session에서 구독중인 방이 있는지 확인한다.
        String previousRoomId = chatRoomSessionTracker.currentRoomOfSession(sessionId);
        // 중복 ENTER가 들어와도 refcount가 중복 증가하지 않도록 바인딩 성공 시에만 입장 처리한다.
        boolean firstEnterForSession = chatRoomSessionTracker.bindSessionToRoom(sessionId, message.getRoomId());
        if (!firstEnterForSession) {
            log.debug("Ignored duplicated ENTER. sessionId={}, roomId={}", sessionId, message.getRoomId());
            return;
        }
        // websocket 1개가 room을 이동한 경우 기존 room refcount를 먼저 감소시킨다.
        if (previousRoomId != null && !previousRoomId.equals(message.getRoomId())) {
            chatRoomService.leaveChatRoom(previousRoomId);
        }
        // 입장 메시지 처리: Redis Topic 구독
        chatRoomService.enterChatRoom(message.getRoomId());
        message.setMessage(message.getSender() + "님이 입장하셨습니다.");
    }

    private void handleQuit(ChatMessage message, String sessionId) {
        // 중복 QUIT/이미 끊긴 세션으로 인한 과도한 감소를 막기 위해 실제 바인딩 해제 시에만 leave 처리한다.
        boolean removed = chatRoomSessionTracker.unbindSessionFromRoom(sessionId, message.getRoomId());
        if (!removed) {
            log.debug("Ignored duplicated QUIT. sessionId={}, roomId={}", sessionId, message.getRoomId());
            return;
        }
        // 퇴장 메시지 처리: 로컬 참조 카운트를 낮춰 유휴 topic 정리 대상에 포함
        // 즉시 unsubscribe 하지 않고 ChatRoomService 스케줄러가 유예시간 후 정리한다.
        chatRoomService.leaveChatRoom(message.getRoomId());
        message.setMessage(message.getSender() + "님이 퇴장하셨습니다.");
    }

    private void validateAndNormalizeMessage(ChatMessage message, String sessionId) {
        if (message == null) {
            throw new IllegalArgumentException("message payload is required");
        }
        if (message.getType() == null) {
            throw new IllegalArgumentException("message type is required");
        }
        if (isBlank(message.getRoomId())) {
            throw new IllegalArgumentException("roomId is required");
        }
        if (isBlank(message.getSender())) {
            throw new IllegalArgumentException("sender is required");
        }

        if ((ChatMessage.MessageType.ENTER.equals(message.getType())
                || ChatMessage.MessageType.QUIT.equals(message.getType()))
                && isBlank(sessionId)) {
            throw new IllegalArgumentException("sessionId is required for ENTER/QUIT");
        }

        // 저장/로그 일관성을 위해 공백을 제거한 값으로 정규화한다.
        message.setRoomId(message.getRoomId().trim());
        message.setSender(message.getSender().trim());
        if (message.getMessage() != null) {
            message.setMessage(message.getMessage().trim());
        }

        // TALK은 실제 사용자 본문이 반드시 필요하다. ENTER/QUIT은 서버 메시지로 덮어쓴다.
        if (ChatMessage.MessageType.TALK.equals(message.getType()) && isBlank(message.getMessage())) {
            throw new IllegalArgumentException("message text is required for TALK type");
        }

        // 존재하지 않는 roomId로 publish 되는 것을 방지한다.
        ChatRoom chatRoom = chatRoomService.findRoomById(message.getRoomId());
        if (chatRoom == null) {
            throw new IllegalArgumentException("chat room not found: " + message.getRoomId());
        }
    }

    private String extractSessionId(SimpMessageHeaderAccessor headerAccessor) {
        return headerAccessor == null ? null : headerAccessor.getSessionId();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
