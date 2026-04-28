package com.travel.intellitravel_pioneer.vo.social;

import lombok.Data;

@Data
public class UserRelationVO {
    private Integer userId;
    private String username;
    private String name;
    private String avatar;
    private String grade;
    private Boolean isFollowing;
    private Boolean isFriend;
    private Boolean isFollowedBy;
}
