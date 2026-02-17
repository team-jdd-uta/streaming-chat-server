package com.example.demo.controller;

import com.example.demo.entity.WatchHistory;
import com.example.demo.model.ChatRoom;
import com.example.demo.model.DTO.WatchHistoryDTO;
import com.example.demo.service.ChatRoomService;
import com.example.demo.service.WatchHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

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
        if (isBlank(roomId)) {
            throw new IllegalArgumentException("roomId is required");
        }
        ChatRoom chatRoom = chatRoomService.findRoomById(roomId.trim());
        if (chatRoom == null) {
            throw new NoSuchElementException("chat room not found: " + roomId);
        }
        return chatRoom;
    }

    // 채팅방 생성
    @PostMapping("/room")
    public ChatRoom createRoom(@RequestParam String name) {
        if (isBlank(name)) {
            throw new IllegalArgumentException("room name is required");
        }
        return chatRoomService.createChatRoom(name.trim());
    }

    // 채팅방 삭제
    @DeleteMapping("/room/{roomId}")
    public ChatRoom deleteRoom(@PathVariable String roomId) {
        if (isBlank(roomId)) {
            throw new IllegalArgumentException("roomId is required");
        }

        ChatRoom deleted = chatRoomService.deleteChatRoom(roomId.trim());
        if (deleted == null) {
            throw new NoSuchElementException("chat room not found: " + roomId);
        }
        return deleted;
    }

    //TODO : controller 단에서 비즈니스 로직이?
    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
