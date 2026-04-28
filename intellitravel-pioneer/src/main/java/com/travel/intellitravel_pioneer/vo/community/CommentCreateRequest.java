package com.travel.intellitravel_pioneer.vo.community;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CommentCreateRequest {
    private String content;
    
    @JsonProperty("parent_comment_id")
    private String parentCommentId;
}
