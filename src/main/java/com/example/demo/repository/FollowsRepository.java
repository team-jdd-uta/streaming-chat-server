package com.example.demo.repository;

import com.example.demo.entity.Follows;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FollowsRepository extends JpaRepository<Follows, FollowsId> {
    List<Follows> findByIdFollowingUserId(String followingUserId);
    List<Follows> findByIdFollowedUserId(String followedUserId);
}

