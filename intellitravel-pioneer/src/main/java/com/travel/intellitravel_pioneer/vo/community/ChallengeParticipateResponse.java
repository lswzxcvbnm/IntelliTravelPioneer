package com.travel.intellitravel_pioneer.vo.community;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChallengeParticipateResponse {
    @JsonProperty("challenge_id")
    private Long challengeId;

    @JsonProperty("action")
    private String action;

    @JsonProperty("status")
    private Integer status;
}
