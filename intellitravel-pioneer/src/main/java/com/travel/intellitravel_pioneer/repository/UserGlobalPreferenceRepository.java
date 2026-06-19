package com.travel.intellitravel_pioneer.repository;

import com.travel.intellitravel_pioneer.po.UserGlobalPreferencePO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserGlobalPreferenceRepository extends JpaRepository<UserGlobalPreferencePO, Long> {
    
    /**
     * 查找用户所有启用的偏好
     */
    List<UserGlobalPreferencePO> findByUserIdAndEnabledTrue(String userId);
    
    /**
     * 查找用户指定类别的所有启用偏好
     */
    List<UserGlobalPreferencePO> findByUserIdAndCategoryAndEnabledTrue(String userId, String category);
    
    /**
     * 查找用户指定类别和键的偏好
     */
    Optional<UserGlobalPreferencePO> findByUserIdAndCategoryAndPreferenceKey(String userId, String category, String preferenceKey);
    
    /**
     * 删除用户指定类别和键的偏好
     */
    void deleteByUserIdAndCategoryAndPreferenceKey(String userId, String category, String preferenceKey);
    
    /**
     * 统计用户偏好数量
     */
    long countByUserId(String userId);
}