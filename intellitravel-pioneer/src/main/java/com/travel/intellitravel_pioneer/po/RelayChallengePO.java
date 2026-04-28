package com.travel.intellitravel_pioneer.po;

import com.travel.intellitravel_pioneer.po.converter.StringListJsonConverter;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "challenge_record")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelayChallengePO {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "post_id")
    private String postId;

    @Column(name = "itinerary_id", length = 36)
    private String itineraryId;

    @Column(name = "proof_images", columnDefinition = "TEXT")
    @Convert(converter = StringListJsonConverter.class)
    private List<String> proofImages;

    @Column(name = "fitness_data", columnDefinition = "TEXT")
    private String fitnessData;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "status")
    private Integer status; // 0: pending, 1: approved, 2: rejected

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
