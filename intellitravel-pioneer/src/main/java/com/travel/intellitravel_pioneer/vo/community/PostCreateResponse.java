package com.travel.intellitravel_pioneer.vo.community;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PostCreateResponse {
    @JsonProperty("post_id")
    private String postId;
    
    @JsonProperty("post_url")
    private String postUrl;
    
    @JsonProperty("created_at")
    private String createdAt;
}
