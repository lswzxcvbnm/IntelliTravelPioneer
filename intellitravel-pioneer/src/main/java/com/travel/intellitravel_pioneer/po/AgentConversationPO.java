package com.travel.intellitravel_pioneer.po;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_conversation")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentConversationPO {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "tags", length = 500)
    private String tags;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Column(name = "itinerary_id", length = 36)
    private String itineraryId;
}
