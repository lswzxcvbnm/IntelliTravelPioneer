package com.travel.intellitravel_pioneer.vo.community;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CommentVO {
    @JsonProperty("comment_id")
    private String commentId;

    private String content;

    @JsonProperty("author_name")
    private String authorName;

    @JsonProperty("author_avatar")
    private String authorAvatar;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("like_count")
    private Integer likeCount;

    @JsonProperty("is_liked")
    private Boolean isLiked;

    @JsonProperty("parent_comment_id")
    private String parentCommentId;

    // child replies for nested display
    private java.util.List<CommentVO> replies;
}
