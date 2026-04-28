package com.travel.intellitravel_pioneer.service;

import com.travel.intellitravel_pioneer.vo.user.UserHealthVO;

public interface UserHealthService {
    
    /**
     * 根据用户名获取用户的健康属性
     */
    UserHealthVO getHealthInformation(String username);

    /**
     * 更新或保存用户的健康属性
     */
    String saveOrUpdateHealthInformation(UserHealthVO userHealthVO);
}