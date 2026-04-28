package com.travel.intellitravel_pioneer.vo.capsule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import com.travel.intellitravel_pioneer.vo.user.UserSimpleVO;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapsuleDetailVO {
    private String id;
    private String title;
    private String content;
    private String decoration;
    private String color;
    private Boolean isPublic;
    private String date; // YYYY-MM-DD
    private String createdAt; // ISO-8601 string

    // Note: Assuming UserSimpleVO is created under com.travel.intellitravel_pioneer.vo.user.UserSimpleVO
    private UserSimpleVO author;
    
    // 高德地图位置
    private LocationVO location;
    
    // 阿里云OSS媒体
    private List<MediaFileVO> images;

    private Integer likesCount;
    private Integer commentsCount;
    private Boolean isLiked;
    private Boolean isSaved;
    private Integer viewCount;

    private PermissionsVO permissions;
}