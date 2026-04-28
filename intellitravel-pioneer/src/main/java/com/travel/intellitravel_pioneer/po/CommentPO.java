package com.travel.intellitravel_pioneer.po;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "community_comment")
@Data
public class CommentPO {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "post_id")
    private String postId;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "parent_comment_id")
    private String parentCommentId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "like_count")
    private Integer likeCount = 0;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
