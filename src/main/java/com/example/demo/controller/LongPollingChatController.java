package com.example.demo.controller;

import com.example.demo.model.ChatMessage;
import com.example.demo.service.ChatRoomService;
import com.example.demo.service.LongPollingService;
import com.example.demo.service.MessageBrokerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.Duration;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/lp")
public class LongPollingChatController {

    private final MessageBrokerService messageBrokerService;
    private final ChatRoomService chatRoomService;
    private final LongPollingService longPollingService;

    @PostMapping("/chat/message")
    public void message(@RequestBody ChatMessage message) {
        log.info("[CONTROLLER] Message received - RoomId: {}, Type: {}, Sender: {}, Message: {}",
                message.getRoomId(), message.getType(), message.getSender(), message.getMessage());

        if (ChatMessage.MessageType.ENTER.equals(message.getType())) {
            chatRoomService.enterChatRoom(message.getRoomId());
            message.setMessage(message.getSender() + "님이 입장하셨습니다.");
        } else if (ChatMessage.MessageType.QUIT.equals(message.getType())) {
            message.setMessage(message.getSender() + "님이 퇴장하셨습니다.");
        }

        log.info("[CONTROLLER] Publishing message to Redis - RoomId: {}, Message: {}",
                message.getRoomId(), message.getMessage());
        messageBrokerService.publish(message.getRoomId(), message);
    }

    @GetMapping("/chat/room/{roomId}/poll")
    public DeferredResult<ChatMessage> poll(
            @PathVariable String roomId,
            @RequestParam(name = "timeoutMs", defaultValue = "25000") long timeoutMs,
            @RequestParam(name = "lastMessageTime", required = false) Long lastMessageTime) {
        log.info("[CONTROLLER] Poll request - RoomId: {}, Timeout: {}ms, LastMessageTime: {}",
                roomId, timeoutMs, lastMessageTime);
        chatRoomService.enterChatRoom(roomId);
        return longPollingService.createWaiter(roomId, lastMessageTime, Duration.ofMillis(timeoutMs));
    }
}
