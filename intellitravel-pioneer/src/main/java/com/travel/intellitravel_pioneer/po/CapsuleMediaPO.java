package com.travel.intellitravel_pioneer.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "capsule_media")
public class CapsuleMediaPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 关联胶囊的id
    private Long capsuleId;

    // 阿里云OSS文件URL
    private String url;

    // 文件类型 'image' | 'video' | 'audio'
    private String fileType;

    // 阿里云OSS缩略图URL (可通过图片处理参数直接生成而不用另存)
    private String thumbnail;

    private Integer width;
    private Integer height;
}