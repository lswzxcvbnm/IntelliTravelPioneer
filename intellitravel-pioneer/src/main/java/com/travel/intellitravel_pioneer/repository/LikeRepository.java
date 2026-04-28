package com.travel.intellitravel_pioneer.repository;

import com.travel.intellitravel_pioneer.po.LikePO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LikeRepository extends JpaRepository<LikePO, Long> {
    Optional<LikePO> findByUserIdAndTargetIdAndType(Integer userId, String targetId, LikePO.LikeType type);
    boolean existsByUserIdAndTargetIdAndType(Integer userId, String targetId, LikePO.LikeType type);
    void deleteAllByTargetIdAndType(String targetId, LikePO.LikeType type);
}
