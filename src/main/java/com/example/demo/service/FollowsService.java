package com.example.demo.service;

import com.example.demo.entity.Follows;
import com.example.demo.model.DTO.CustomerDTO;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.FollowsRepository;
import org.apache.ibatis.session.SqlSession;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FollowsService {

    private final FollowsRepository followsRepository;
    private final CustomerRepository customerRepository;
    private final SqlSession sqlSession;

    public FollowsService(FollowsRepository followsRepository,
                        CustomerRepository customerRepository,
                        SqlSession sqlSession) {
        this.followsRepository = followsRepository;
        this.customerRepository = customerRepository;
        this.sqlSession = sqlSession;
    }



    public int getFollowingCount(String customerId) {
        //내가 팔로우하는 사람들 수
        return sqlSession.selectOne("com.example.mapper.FollowsMapper.selectFollowingCount", customerId);
    }

    public int getFollowedCount(String customerId) {
        //나를 팔로우하는 사람들 수
        return sqlSession.selectOne("com.example.mapper.FollowsMapper.selectFollowedCount", customerId);
    }

    public boolean subscribeUser(String fromCustomerId, String toCustomerId) {
        try {
            Follows follow = Follows.builder()
                    .followingUserId(fromCustomerId)
                    .followedUserId(toCustomerId)
                    .followedAt(LocalDateTime.now())
                    .build();

            followsRepository.save(follow);
            return true;
        }
        catch (DataAccessException e) {
            System.err.println("Failed to save follow relationship: " + e.getMessage());
            return false;
        }
    }

    public List<CustomerDTO> getFollowingList(String customerId, int offset, int limit) {
        //내가 팔로우하는 사람들
        Map<String, Object> params = new HashMap<>();
        params.put("userId", customerId);
        params.put("offset", offset);
        params.put("limit", limit);

        System.out.println("Getting following list for userId: " + customerId + ", offset: " + offset + ", limit: " + limit);
        System.out.println(params);
        return sqlSession.selectList("com.example.mapper.FollowsMapper.selectFollowingByUserId", params);
    }

    public List<CustomerDTO> getFollowerList(String customerId, int offset, int limit) {

        Map<String, Object> params = new HashMap<>();
        params.put("userId", customerId);
        params.put("offset", offset);
        params.put("limit", limit);

        System.out.println(params);

        System.out.println("Getting follower list for userId: " + customerId + ", offset: " + offset + ", limit: " + limit);

        return sqlSession.selectList("com.example.mapper.FollowsMapper.selectFollowedByUserId", params);
    }

}
