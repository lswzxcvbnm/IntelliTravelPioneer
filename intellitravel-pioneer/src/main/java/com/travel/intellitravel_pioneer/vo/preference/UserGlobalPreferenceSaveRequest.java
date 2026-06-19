package com.travel.intellitravel_pioneer.vo.preference;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户全局偏好保存请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserGlobalPreferenceSaveRequest {

    /**
     * 偏好类别：STYLE, DIET, FITNESS, SPECIAL, CUSTOM
     */
    private String category;

    /**
     * 偏好键
     */
    private String preferenceKey;

    /**
     * 偏好值/描述
     */
    private String preferenceValue;

    /**
     * 是否启用（默认true）
     */
    private Boolean enabled;
}