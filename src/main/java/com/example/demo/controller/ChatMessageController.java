package com.example.demo.controller;

import com.example.demo.model.ChatMessage;
import com.example.demo.service.ChatRoomService;
import com.example.demo.service.MessageBrokerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@RequiredArgsConstructor
@Controller
@Slf4j
public class ChatMessageController {

    private final MessageBrokerService messageBrokerService;
    private final ChatRoomService chatRoomService;

    /**
     * WebSocket "/pub/chat/message"로 들어오는 메시징을 처리
     */
    @MessageMapping("/chat/message")
    public void message(ChatMessage message) {
        if (ChatMessage.MessageType.ENTER.equals(message.getType())) {
            // 입장 메시지 처리: Redis Topic 구독
            chatRoomService.enterChatRoom(message.getRoomId());
            message.setMessage(message.getSender() + "님이 입장하셨습니다.");
        } else if (ChatMessage.MessageType.QUIT.equals(message.getType())) {
            // 퇴장 메시지 처리: (선택적) Redis Topic 구독 해지
            // chatRoomService.leaveChatRoom(message.getRoomId()); // 실제로는 연결 끊어질 때 자동으로 처리되므로 주석 처리
            message.setMessage(message.getSender() + "님이 퇴장하셨습니다.");
        }
        // 클라이언트로부터 받은 메시지를 Redis로 발행
        messageBrokerService.publish(message.getRoomId(), message);
    }
}
