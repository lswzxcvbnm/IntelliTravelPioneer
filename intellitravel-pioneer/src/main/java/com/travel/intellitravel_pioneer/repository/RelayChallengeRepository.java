package com.travel.intellitravel_pioneer.repository;

import com.travel.intellitravel_pioneer.po.RelayChallengePO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RelayChallengeRepository extends JpaRepository<RelayChallengePO, Long> {
    
    List<RelayChallengePO> findByUserId(Integer userId);
    
    List<RelayChallengePO> findByPostId(String postId);
    
    @org.springframework.data.jpa.repository.Query("SELECT r FROM RelayChallengePO r, PostPO p WHERE r.postId = p.id AND p.userId = :userId AND p.itineraryId IS NOT NULL")
    List<RelayChallengePO> findByCreatorId(@org.springframework.data.repository.query.Param("userId") Integer userId);

    boolean existsByUserIdAndPostId(Integer userId, String postId);

    Optional<RelayChallengePO> findByUserIdAndPostId(Integer userId, String postId);

    List<RelayChallengePO> findByItineraryIdAndStatusOrderByDurationMinutesAsc(String itineraryId, Integer status);
}
