package com.example.demo.repository;

import com.example.demo.entity.Follows;
import com.example.demo.entity.FollowsId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FollowsRepository extends JpaRepository<Follows, FollowsId> {
}
