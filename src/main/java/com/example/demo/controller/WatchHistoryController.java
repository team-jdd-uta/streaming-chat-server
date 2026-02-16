package com.example.demo.controller;

import com.example.demo.entity.WatchHistory;
import com.example.demo.service.WatchHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/watch_history")
@RequiredArgsConstructor
public class WatchHistoryController {

    private final WatchHistoryService watchHistoryService;

    @PostMapping
    public ResponseEntity<?> insertWatchHistory(@RequestBody Map<String,String> payload) {
        WatchHistory wh = new WatchHistory();
        wh.setUserId(String.valueOf(payload.get("userId")));
        wh.setVideoId(Long.valueOf(payload.get("videoId")));
        wh.setStartedAt(LocalDateTime.now());
        wh.setEndedAt(LocalDateTime.now());
        //종료 시점, 동영상 스트리밍을 관리하는 ws가 끊어지거나 사용자가 영상을 unmount했을때 업데이트하는 로직 필요

        boolean success = watchHistoryService.insertWatchHistory(wh);
        return success ? ResponseEntity.ok().build() :
                ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Insert failed");
    }
}