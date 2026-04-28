package com.travel.intellitravel_pioneer.vo.community;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CommentCreateResponse {
    @JsonProperty("comment_id")
    private String commentId;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("current_comment_count")
    private Integer currentCommentCount;
}
