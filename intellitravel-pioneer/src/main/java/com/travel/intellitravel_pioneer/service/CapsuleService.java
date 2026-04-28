package com.travel.intellitravel_pioneer.service;

import com.travel.intellitravel_pioneer.vo.capsule.CapsuleDetailVO;
import com.travel.intellitravel_pioneer.vo.capsule.CapsuleListItemVO;

import java.util.List;

public interface CapsuleService {
    
    /**
     * 创建时空胶囊
     */
    String createCapsule(Long userId, CapsuleDetailVO capsuleDetail);

    /**
     * 删除胶囊
     */
    void deleteCapsule(Long capsuleId, Long userId);

    /**
     * 更新胶囊
     */
    void updateCapsule(Long capsuleId, Long userId, CapsuleDetailVO capsuleDetail);

    /**
     * 获取用户创建的所有胶囊
     */
    List<CapsuleListItemVO> getUserCapsules(Long userId);

    /**
     * 获取指定地点的所有胶囊（简版列表）
     */
    List<CapsuleListItemVO> getCapsulesByLocation(String locationName);

    /**
     * 获取指定地点的所有胶囊（简版列表），优先 city/adcode 过滤
     */
    List<CapsuleListItemVO> getCapsulesByLocation(String locationName, String city);

    /**
     * 根据经纬度和半径获取附近胶囊（简版列表）
     */
    List<CapsuleListItemVO> getCapsulesByLocation(Double latitude, Double longitude, Double radiusMeters);

    /**
     * 根据经纬度和半径判断是否存在同一个地点的胶囊
     */
    boolean isCapsuleAtSameLocation(Long userId, Double latitude, Double longitude, Double radiusMeters);

    /**
     * 获取胶囊详情
     */
    CapsuleDetailVO getCapsuleDetail(Long capsuleId, Long currentUserId);
}
