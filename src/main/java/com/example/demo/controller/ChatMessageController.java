package com.example.demo.controller;

import com.example.demo.model.ChatMessage;
import com.example.demo.model.ChatRoom;
import com.example.demo.service.ChatRoomService;
import com.example.demo.service.MessageBrokerService;
import com.example.demo.service.WebSocketSessionRegistry;
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
    private final WebSocketSessionRegistry webSocketSessionRegistry;

    /**
     * WebSocket "/pub/chat/message"로 들어오는 메시징을 처리
     */
    @MessageMapping("/chat/message")
    public void message(ChatMessage message, SimpMessageHeaderAccessor headerAccessor) {
        try {
            validateMessage(message);
        } catch (IllegalArgumentException exception) {
            forceCloseSessionOnValidationError(headerAccessor, exception);
            throw exception;
        }

        if (ChatMessage.MessageType.ENTER.equals(message.getType())) {
            // 입장 메시지 처리: Redis Topic 구독
            chatRoomService.enterChatRoom(message.getRoomId());
            message.setMessage(message.getSender() + "님이 입장하셨습니다.");
        } else if (ChatMessage.MessageType.QUIT.equals(message.getType())) {
            // 퇴장 메시지 처리: 로컬 참조 카운트를 낮춰 유휴 topic 정리 대상에 포함
            // 즉시 unsubscribe 하지 않고 ChatRoomService 스케줄러가 유예시간 후 정리한다.
            chatRoomService.leaveChatRoom(message.getRoomId());
            message.setMessage(message.getSender() + "님이 퇴장하셨습니다.");
        }
        // 클라이언트로부터 받은 메시지를 Redis로 발행
        messageBrokerService.publish(message.getRoomId(), message);
    }

    private void forceCloseSessionOnValidationError(
            SimpMessageHeaderAccessor headerAccessor,
            IllegalArgumentException exception
    ) {
        String sessionId = headerAccessor == null ? null : headerAccessor.getSessionId();
        if (isBlank(sessionId)) {
            return;
        }
        // 검증 실패 메시지를 보낸 세션만 종료해 잘못된 클라이언트를 빠르게 분리한다.
        boolean closed = webSocketSessionRegistry.closeSession(sessionId, "Invalid payload");
        if (closed) {
            log.warn("Closed websocket session due to invalid message payload. sessionId={}, reason={}",
                    sessionId, exception.getMessage());
        }
    }

    private void validateMessage(ChatMessage message) {
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

        // TALK은 실제 사용자 본문이 반드시 필요하다. ENTER/QUIT은 서버 메시지로 덮어쓴다.
        if (ChatMessage.MessageType.TALK.equals(message.getType()) && isBlank(message.getMessage())) {
            throw new IllegalArgumentException("message text is required for TALK type");
        }

        // 존재하지 않는 roomId로 publish 되는 것을 방지한다.
        ChatRoom chatRoom = chatRoomService.findRoomById(message.getRoomId());
        if (chatRoom == null) {
            throw new IllegalArgumentException("chat room not found: " + message.getRoomId());
        }

        // 저장/로그 일관성을 위해 공백을 제거한 값으로 정규화한다.
        message.setRoomId(message.getRoomId().trim());
        message.setSender(message.getSender().trim());
        if (message.getMessage() != null) {
            message.setMessage(message.getMessage().trim());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
