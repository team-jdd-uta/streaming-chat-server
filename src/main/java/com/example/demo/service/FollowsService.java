package com.example.demo.service;

import com.example.demo.model.DTO.CustomerDTO;
import com.example.demo.repository.FollowsRepository;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FollowsService {

    FollowsRepository followsRepository;

    SqlSession sqlSession;

    public List<CustomerDTO> getFollowingList(String customerId, int offset, int limit) {
        //내가 팔로우하는 사람들
        Map<String, Object> params = new HashMap<>();
        params.put("userId", customerId);
        params.put("offset", offset);
        params.put("limit", limit);

        return sqlSession.selectList("com.example.mapper.FollowsMapper.selectFollowingByuserId", params);
    }

    public List<CustomerDTO> getFollowerList(String customerId, int offset, int limit) {

        Map<String, Object> params = new HashMap<>();
        params.put("userId", customerId);
        params.put("offset", offset);
        params.put("limit", limit);

        return sqlSession.selectList("com.example.mapper.FollowsMapper.selectFollowedByUserId", params);
    }

}
