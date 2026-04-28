package com.travel.intellitravel_pioneer.po;

import com.travel.intellitravel_pioneer.po.converter.StringListJsonConverter;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "community_post")
@Data
public class PostPO {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "summary_id")
    private String summaryId;

    @Column(name = "title")
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "images", columnDefinition = "TEXT")
    @Convert(converter = StringListJsonConverter.class)
    private List<String> images;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "is_public")
    private Boolean isPublic;

    @Column(name = "tags")
    private String tags;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "like_count")
    private Integer likeCount = 0;

    @Column(name = "comment_count")
    private Integer commentCount = 0;

    @Column(name = "collect_count")
    private Integer collectCount = 0;

    @Column(name = "itinerary_id", length = 36)
    private String itineraryId;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
