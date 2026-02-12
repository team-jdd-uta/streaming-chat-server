package com.example.demo.service;

import com.example.demo.entity.Comment;
import com.example.demo.repository.CommentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CommentService {

    private static final Logger logger = LoggerFactory.getLogger(CommentService.class);

    @Autowired
    CommentRepository commentRepository;

    public List<Comment> getUserComments(String userId) {
        List<Comment> comments = commentRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return comments;
    }

    public List<Comment> getCommentsWithUserIdRoomId(String userId, String roomId) {
        return commentRepository.findByUserIDAndRoomId(userId, roomId);
    }
    public List<Comment> getCommentsWithRoomId(String roomId) {
        return commentRepository.findByRoomId(roomId);
    }

    public List<Comment> getCommentsWithRoomIdAndDateRange( String roomId, String startDate, String endDate) {
        return commentRepository.findByRoomIdAndCreatedAtBetween( roomId, startDate, endDate);
    }
}
