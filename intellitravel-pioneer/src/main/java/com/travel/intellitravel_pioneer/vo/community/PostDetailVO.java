package com.travel.intellitravel_pioneer.vo.community;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PostDetailVO {
    @JsonProperty("post_id")
    private String postId;

    private String title;

    @JsonProperty("preview_content")
    private String previewContent; // For list view

    @JsonProperty("content")
    private String content; // For detail view

    @JsonProperty("preview_images")
    private List<String> previewImages; // For list view

    @JsonProperty("images")
    private List<String> images; // For detail view

    private AuthorVO author;

    @JsonProperty("like_count")
    private Integer likeCount;

    @JsonProperty("comment_count")
    private Integer commentCount;

    @JsonProperty("collect_count")
    private Integer collectCount;

    @JsonProperty("is_liked")
    private Boolean isLiked;

    @JsonProperty("is_collected")
    private Boolean isCollected;

    @JsonProperty("created_at")
    private String createdAt;

    private List<String> tags;

    private SummaryVO summary;

    private List<CommentVO> comments;

    @JsonProperty("itinerary_id")
    private String itineraryId;
}
