package com.example.demo.controller;

import com.example.demo.model.ChatRoom;
import com.example.demo.service.ChatRoomService;
import com.example.demo.service.ChatRoomSessionTracker;
import com.example.demo.service.WebSocketDrainService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ops/rooms")
@RequiredArgsConstructor
public class OpsRoomStatusController {

    private final ChatRoomService chatRoomService;
    private final ChatRoomSessionTracker chatRoomSessionTracker;
    private final WebSocketDrainService drainService;

    @GetMapping("/status")
    public Map<String, Object> status() {
        List<ChatRoom> rooms = chatRoomService.findAllRoom();
        Map<String, ChatRoomService.TopicSnapshot> topicSnapshots = chatRoomService.topicSnapshots();
        Map<String, Integer> wsCounts = chatRoomSessionTracker.roomSessionCounts();

        List<Map<String, Object>> rows = rooms.stream()
                .map(room -> {
                    ChatRoomService.TopicSnapshot topic = topicSnapshots.get(room.getRoomId());
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("roomId", room.getRoomId());
                    row.put("name", room.getName());
                    row.put("websocketSessions", wsCounts.getOrDefault(room.getRoomId(), 0));
                    row.put("redisListenerCount", topic == null ? 0 : 1);
                    row.put("redisLocalRefCount", topic == null ? 0 : topic.localRefCount());
                    row.put("topicLastTouchedAt", topic == null || topic.lastTouchedAt() == null
                            ? null
                            : topic.lastTouchedAt().toString());
                    return row;
                })
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("generatedAt", Instant.now().toString());
        response.put("draining", drainService.isDraining());
        response.put("roomCount", rows.size());
        response.put("rooms", rows);
        return response;
    }
}
