package com.travel.intellitravel_pioneer.vo.capsule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CapsuleListItemVO {
    private String id;
    private String title;
    private String color;
    // 封面大图(可以取images里的第一张缩略图+阿里云OSS处理)
    private String coverImage;
    private String date; //发生日期
    private String authorName;
    private SimpleLocationVO location;
    private String createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SimpleLocationVO {
        private String name;
        private Double lat;
        private Double lng;
    }
}