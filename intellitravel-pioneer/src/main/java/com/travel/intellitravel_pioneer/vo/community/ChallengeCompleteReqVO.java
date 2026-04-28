package com.travel.intellitravel_pioneer.vo.community;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ChallengeCompleteReqVO {
    
    @JsonProperty("proof_images")
    private List<String> proofImages;

    @JsonProperty("fitness_data")
    private String fitnessData;

    @JsonProperty("duration_minutes")
    private Integer durationMinutes;
}
