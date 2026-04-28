package com.travel.intellitravel_pioneer.vo.community;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class PostCreateRequest {
    @JsonProperty("summary_id")
    private String summaryId;

    private String title;
    
    private String content;
    
    private List<String> images;
    
    @JsonProperty("is_public")
    private Boolean isPublic = true;
    
    private String tags;
    
    @JsonProperty("itinerary_id")
    private String itineraryId;
}
