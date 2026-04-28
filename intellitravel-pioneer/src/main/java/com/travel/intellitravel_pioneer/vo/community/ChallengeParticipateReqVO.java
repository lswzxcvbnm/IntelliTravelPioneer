package com.travel.intellitravel_pioneer.vo.community;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ChallengeParticipateReqVO {

    @JsonProperty("action")
    private String action;
    
    @JsonProperty("post_id")
    private String postId;

    @JsonProperty("itinerary_id")
    private String itineraryId;
}
