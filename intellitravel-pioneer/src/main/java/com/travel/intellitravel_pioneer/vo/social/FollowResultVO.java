package com.travel.intellitravel_pioneer.vo.social;

import lombok.Data;

@Data
public class FollowResultVO {
    private boolean success;
    private boolean isNowFollowing;
    private boolean isNowFriend;
    private String message;
}
