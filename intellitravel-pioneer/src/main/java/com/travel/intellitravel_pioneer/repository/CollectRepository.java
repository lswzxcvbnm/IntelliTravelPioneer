package com.travel.intellitravel_pioneer.repository;

import com.travel.intellitravel_pioneer.po.CollectPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CollectRepository extends JpaRepository<CollectPO, Long> {
    Optional<CollectPO> findByUserIdAndPostId(Integer userId, String postId);
    boolean existsByUserIdAndPostId(Integer userId, String postId);
    org.springframework.data.domain.Page<CollectPO> findByUserId(Integer userId, org.springframework.data.domain.Pageable pageable);
    void deleteAllByPostId(String postId);
}
