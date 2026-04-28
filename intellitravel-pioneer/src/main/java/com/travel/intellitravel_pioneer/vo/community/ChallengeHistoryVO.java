package com.travel.intellitravel_pioneer.vo.community;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChallengeHistoryVO {
    private Long id;

    @JsonProperty("user_id")
    private Integer userId;

    private String username;

    @JsonProperty("post_id")
    private String postId;

    @JsonProperty("itinerary_id")
    private String itineraryId;

    @JsonProperty("post_title")
    private String postTitle;

    @JsonProperty("proof_images")
    private List<String> proofImages;

    @JsonProperty("fitness_data")
    private String fitnessData;

    @JsonProperty("duration_minutes")
    private Integer durationMinutes;

    private Integer status; // 0: pending, 1: approved, 2: rejected

    private String role; // "creator" or "participant"

    @JsonProperty("created_at")
    private String createdAt;
}
