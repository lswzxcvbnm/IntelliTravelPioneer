package com.travel.intellitravel_pioneer.vo.community;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InteractionResponse {
    @JsonProperty("current_like_count")
    private Integer currentLikeCount;

    @JsonProperty("is_liked")
    private Boolean isLiked;

    @JsonProperty("current_collect_count")
    private Integer currentCollectCount;

    @JsonProperty("collect_count")
    private Integer collectCount;

    @JsonProperty("is_collected")
    private Boolean isCollected;
}
