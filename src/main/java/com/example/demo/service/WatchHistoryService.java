package com.example.demo.service;

import com.example.demo.entity.WatchHistory;
import org.apache.ibatis.session.SqlSession;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WatchHistoryService {
    SqlSession sqlSession;

    public List<WatchHistory> getRecentWatchHistoriesByUserId(String userId, int limit, int offset) {
        Map<String, Object> params = new HashMap<>();
        params.put("user_Id", userId);
        params.put("limit", limit);
        params.put("offset", offset);

        return sqlSession.selectList("com.example.mapper.WatchHistoryMapper.selectRecentWatchHistoriesByUserId", params);

    }
}
