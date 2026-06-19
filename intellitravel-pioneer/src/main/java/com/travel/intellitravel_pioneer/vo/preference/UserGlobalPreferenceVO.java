package com.travel.intellitravel_pioneer.vo.preference;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户全局偏好响应VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserGlobalPreferenceVO {

    private Long id;
    private String userId;
    private String category;
    private String preferenceKey;
    private String preferenceValue;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}