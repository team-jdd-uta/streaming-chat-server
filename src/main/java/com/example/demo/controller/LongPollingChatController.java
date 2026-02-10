package com.example.demo.controller;

import com.example.demo.model.ChatMessage;
import com.example.demo.service.ChatRoomService;
import com.example.demo.service.LongPollingService;
import com.example.demo.service.MessageBrokerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.Duration;

@RequiredArgsConstructor
@RestController
@RequestMapping("/lp")
public class LongPollingChatController {

    private final MessageBrokerService messageBrokerService;
    private final ChatRoomService chatRoomService;
    private final LongPollingService longPollingService;

    @PostMapping("/chat/message")
    public void message(@RequestBody ChatMessage message) {
        if (ChatMessage.MessageType.ENTER.equals(message.getType())) {
            chatRoomService.enterChatRoom(message.getRoomId());
            message.setMessage(message.getSender() + "님이 입장하셨습니다.");
        } else if (ChatMessage.MessageType.QUIT.equals(message.getType())) {
            message.setMessage(message.getSender() + "님이 퇴장하셨습니다.");
        }
        messageBrokerService.publish(message.getRoomId(), message);
    }

    @GetMapping("/chat/room/{roomId}/poll")
    public DeferredResult<ChatMessage> poll(
            @PathVariable String roomId,
            @RequestParam(name = "timeoutMs", defaultValue = "25000") long timeoutMs
    ) {
        chatRoomService.enterChatRoom(roomId);
        return longPollingService.createWaiter(roomId, Duration.ofMillis(timeoutMs));
    }
}
