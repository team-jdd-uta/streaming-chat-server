package com.example.demo.repository;

import com.example.demo.entity.WatchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface WatchHistoryRepository extends JpaRepository<WatchHistory, String> {

    @Query(value = "SELECT * FROM watch_history WHERE user_id = :userId", nativeQuery = true)
    List<WatchHistory> findByUserIdNative(@Param("userId") String userId);

    @Query(value = "SELECT * FROM watch_history WHERE video_id = :videoId", nativeQuery = true)
    List<WatchHistory> findByVideoIdNative(@Param("videoId") Long videoId);
}

