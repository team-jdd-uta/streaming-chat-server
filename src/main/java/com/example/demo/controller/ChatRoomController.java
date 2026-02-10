package com.example.demo.controller;

import com.example.demo.model.ChatRoom;
import com.example.demo.service.ChatRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/chat")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    // 모든 채팅방 목록 반환
    @GetMapping("/rooms")
    public List<ChatRoom> findAllRoom() {
        return chatRoomService.findAllRoom();
    }

    // 특정 채팅방 반환
    @GetMapping("/room/{roomId}")
    public ChatRoom findRoomById(@PathVariable String roomId) {
        return chatRoomService.findRoomById(roomId);
    }

    // 채팅방 생성
    @PostMapping("/room")
    public ChatRoom createRoom(@RequestParam String name) {
        return chatRoomService.createChatRoom(name);
    }
}
