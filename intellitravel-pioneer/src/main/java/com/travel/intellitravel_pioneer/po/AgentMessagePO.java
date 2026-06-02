package com.travel.intellitravel_pioneer.po;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_message")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentMessagePO {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "conversation_id", length = 36, nullable = false)
    private String conversationId;

    @Column(name = "role", length = 20, nullable = false)
    private String role;

    @Column(name = "content", columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(name = "tool_calls", columnDefinition = "TEXT")
    private String toolCalls;

    @Column(name = "create_time")
    private LocalDateTime createTime;
}
