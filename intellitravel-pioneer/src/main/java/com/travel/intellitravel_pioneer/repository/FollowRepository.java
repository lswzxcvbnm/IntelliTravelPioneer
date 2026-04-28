package com.travel.intellitravel_pioneer.repository;

import com.travel.intellitravel_pioneer.po.FollowPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FollowRepository extends JpaRepository<FollowPO, Integer> {

    Optional<FollowPO> findByFollowerIdAndFollowingId(Integer followerId, Integer followingId);

    List<FollowPO> findByFollowerIdOrderByCreatedAtDesc(Integer followerId);

    List<FollowPO> findByFollowingIdOrderByCreatedAtDesc(Integer followingId);

    @Query("SELECT COUNT(f) FROM FollowPO f WHERE f.followingId = :userId")
    long countByFollowingId(@Param("userId") Integer userId);

    @Query("SELECT COUNT(f) FROM FollowPO f WHERE f.followerId = :userId")
    long countByFollowerId(@Param("userId") Integer userId);

    void deleteByFollowerIdAndFollowingId(Integer followerId, Integer followingId);
}
