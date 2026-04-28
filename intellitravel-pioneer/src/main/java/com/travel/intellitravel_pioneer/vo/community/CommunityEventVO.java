package com.travel.intellitravel_pioneer.vo.community;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityEventVO {
    private Long eventId;
    private String eventType;
    private Integer actorUserId;
    private String actorUsername;
    private Integer receiverUserId;
    private String receiverUsername;
    private String postId;
    private Long challengeId;
    private String eventDetail;
    private String createdAt;
}
