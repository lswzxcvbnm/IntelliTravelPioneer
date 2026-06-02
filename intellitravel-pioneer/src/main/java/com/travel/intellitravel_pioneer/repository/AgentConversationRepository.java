package com.travel.intellitravel_pioneer.repository;

import com.travel.intellitravel_pioneer.po.AgentConversationPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AgentConversationRepository extends JpaRepository<AgentConversationPO, String> {

    List<AgentConversationPO> findByUserIdOrderByUpdateTimeDesc(String userId);

    List<AgentConversationPO> findByUserIdAndTitleContainingOrderByUpdateTimeDesc(String userId, String title);

    @Query("SELECT c FROM AgentConversationPO c WHERE c.userId = :userId ORDER BY c.createTime DESC")
    List<AgentConversationPO> findByUserIdOrderByCreateTimeDesc(@Param("userId") String userId);

    @Query("SELECT c FROM AgentConversationPO c WHERE c.userId = :userId ORDER BY c.updateTime ASC")
    List<AgentConversationPO> findByUserIdOrderByUpdateTimeAsc(@Param("userId") String userId);

    Optional<AgentConversationPO> findTopByItineraryIdOrderByUpdateTimeDesc(String itineraryId);
}
