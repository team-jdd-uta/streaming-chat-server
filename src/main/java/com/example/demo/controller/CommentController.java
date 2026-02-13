package com.example.demo.controller;

import com.example.demo.entity.Comment;
import com.example.demo.service.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/comments")
@Tag(name = "댓글", description = "댓글 관리 API")
@CrossOrigin(origins = "*")
public class CommentController {

    private static final Logger logger = LoggerFactory.getLogger(CommentController.class);

    @Autowired
    CommentService commentService;

    @Operation(summary = "사용자 댓글 조회", description = "특정 사용자의 모든 댓글을 조회합니다")
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Comment>> getCommentsWithUserId(@PathVariable String userId) {
        try {
            List<Comment> comments = commentService.getUserComments(userId);
            return ResponseEntity.ok(comments);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    @GetMapping("/user/{userId}/room/{roomId}")
    @Operation(summary = "특정 영상 특정 사용자 댓글 조회", description = "특정 영상에 단 특정 사용자의 모든 댓글을 조회합니다")
    public ResponseEntity<List<Comment>> getCommentsWithUserIdRoomId(@PathVariable String userId,
                                                                     @PathVariable String roomId) {
        try {
            List<Comment> comments = commentService.getCommentsWithUserIdRoomId(userId, roomId);
            return ResponseEntity.ok(comments);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/room/{roomId}")
    @Operation(summary = "영상 댓글 전체조회", description = "특정 영상의 모든 댓글을 조회합니다")
    public ResponseEntity<List<Comment>> getCommentsFromRoomId(@PathVariable String roomId) {
        try {
            List<Comment> comments = commentService.getCommentsWithRoomId(roomId);
            return ResponseEntity.ok(comments);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/room/{roomId}/date/{startDate}/{endDate}")
    @Operation(summary = "영상 댓글 기간별 조회", description = "특정 영상의 댓글을 범위 조회 합니다. 영상 건너뛰기 시에 사용")
    public ResponseEntity<List<Comment>> getCommentsWithRoomIdAndDateRange(@PathVariable String roomId,
                                                                                 @PathVariable String startDate,
                                                                                 @PathVariable String endDate) {
        try {
            List<Comment> comments = commentService.getCommentsWithRoomIdAndDateRange( roomId, startDate, endDate);
            return ResponseEntity.ok(comments);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
