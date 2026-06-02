package com.travel.intellitravel_pioneer.repository;

import com.travel.intellitravel_pioneer.po.AgentMessagePO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentMessageRepository extends JpaRepository<AgentMessagePO, String> {

    List<AgentMessagePO> findByConversationIdOrderByCreateTimeAsc(String conversationId);

    void deleteByConversationId(String conversationId);
}
