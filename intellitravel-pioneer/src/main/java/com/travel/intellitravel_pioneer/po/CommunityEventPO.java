package com.travel.intellitravel_pioneer.po;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "community_event", indexes = {
        @Index(name = "idx_receiver_created", columnList = "receiver_user_id, created_at")
})
@Data
public class CommunityEventPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id", nullable = false)
    private Integer actorUserId;

    @Column(name = "receiver_user_id", nullable = false)
    private Integer receiverUserId;

    @Column(name = "post_id")
    private String postId;

    @Column(name = "challenge_id")
    private Long challengeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private EventType eventType;

    @Column(name = "event_detail", nullable = false, length = 512)
    private String eventDetail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public enum EventType {
        POST_LIKED,
        POST_COLLECTED,
        POST_COMMENTED,
        CHALLENGE_PARTICIPATED,
        CHALLENGE_COMPLETED
    }
}
