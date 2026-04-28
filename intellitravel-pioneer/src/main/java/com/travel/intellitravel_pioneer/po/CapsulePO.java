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
import jakarta.persistence.Column;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "capsule")
public class CapsulePO {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 作者ID
    private Long userId;

    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    private String decoration;
    private String color;
    private Boolean isPublic;
    
    // 胶囊发生日期
    private String capsuleDate;
    
    // 定位信息 (高德地图)
    private String locationName;
    private String address;
    private Double latitude;
    private Double longitude;
    private String city;
    private String adcode;
    private String country;

    // 统计信息
    private Integer likesCount;
    private Integer commentsCount;
    private Integer viewCount;

    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (likesCount == null) likesCount = 0;
        if (commentsCount == null) commentsCount = 0;
        if (viewCount == null) viewCount = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
