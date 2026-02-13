package com.example.demo.service;

import com.example.demo.entity.WatchHistory;
import com.example.demo.model.DTO.WatchHistoryDTO;
import org.apache.ibatis.session.SqlSession;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WatchHistoryService {
    private final SqlSession sqlSession;

    public WatchHistoryService(SqlSession sqlSession) {
        this.sqlSession = sqlSession;
    }

    public List<WatchHistoryDTO> getRecentWatchHistoriesByUserId(String userId, int offset, int limit) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("limit", limit);
        params.put("offset", offset);

        return sqlSession.selectList("com.example.mapper.WatchHistoryMapper.selectRecentWatchHistoriesByUserId", params);

    }
}
