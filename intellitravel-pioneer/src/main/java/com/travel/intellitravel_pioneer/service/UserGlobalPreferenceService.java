package com.travel.intellitravel_pioneer.service;

import com.travel.intellitravel_pioneer.vo.preference.UserGlobalPreferenceSaveRequest;
import com.travel.intellitravel_pioneer.vo.preference.UserGlobalPreferenceVO;

import java.util.List;
import java.util.Map;

/**
 * 用户全局偏好服务接口（跨对话知识共享）
 */
public interface UserGlobalPreferenceService {

    /**
     * 获取用户所有启用的偏好
     */
    List<UserGlobalPreferenceVO> getUserPreferences(String userId);

    /**
     * 获取用户指定类别的偏好
     */
    List<UserGlobalPreferenceVO> getUserPreferencesByCategory(String userId, String category);

    /**
     * 保存或更新用户偏好
     */
    UserGlobalPreferenceVO saveOrUpdatePreference(String userId, UserGlobalPreferenceSaveRequest request);

    /**
     * 批量保存用户偏好
     */
    List<UserGlobalPreferenceVO> batchSavePreferences(String userId, List<UserGlobalPreferenceSaveRequest> requests);

    /**
     * 删除用户偏好
     */
    void deletePreference(String userId, String category, String preferenceKey);

    /**
     * 切换偏好启用状态
     */
    UserGlobalPreferenceVO togglePreference(String userId, String category, String preferenceKey, boolean enabled);

    /**
     * 构建用户全局偏好上下文（用于注入AI提示词）
     * @return 格式化的偏好文本，如无偏好返回null
     */
    String buildGlobalPreferenceContext(String userId);

    /**
     * 获取所有可用的偏好类别定义
     */
    Map<String, Object> getPreferenceCategories();
}