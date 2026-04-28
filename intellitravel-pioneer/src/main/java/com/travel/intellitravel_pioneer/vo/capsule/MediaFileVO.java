package com.travel.intellitravel_pioneer.vo.capsule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaFileVO {
    private String id;
    private String url;        // 阿里云OSS直链
    private String type;       // 'image' | 'video' | 'audio'
    private String thumbnail;  // 阿里云OSS图片处理产生(如 ?x-oss-process=image/resize,m_fill,w_300,h_300)
    private Integer width;
    private Integer height;
}