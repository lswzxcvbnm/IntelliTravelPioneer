package com.travel.intellitravel_pioneer.controller;

import com.travel.intellitravel_pioneer.service.UserGlobalPreferenceService;
import com.travel.intellitravel_pioneer.util.TokenUtil;
import com.travel.intellitravel_pioneer.vo.common.Response;
import com.travel.intellitravel_pioneer.vo.preference.UserGlobalPreferenceSaveRequest;
import com.travel.intellitravel_pioneer.vo.preference.UserGlobalPreferenceVO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户全局偏好控制器（跨对话知识共享）
 */
@RestController
@RequestMapping("/api/preference")
public class UserGlobalPreferenceController {

    @Autowired
    private UserGlobalPreferenceService preferenceService;

    @Autowired
    private TokenUtil tokenUtil;

    /**
     * 获取用户所有偏好
     */
    @GetMapping
    public Response<List<UserGlobalPreferenceVO>> getUserPreferences(@RequestHeader("token") String token) {
        String userId = String.valueOf(tokenUtil.getUser(token).getId());
        return Response.buildSuccess(preferenceService.getUserPreferences(userId));
    }

    /**
     * 获取用户指定类别的偏好
     */
    @GetMapping("/{category}")
    public Response<List<UserGlobalPreferenceVO>> getUserPreferencesByCategory(
            @RequestHeader("token") String token,
            @PathVariable String category) {
        String userId = String.valueOf(tokenUtil.getUser(token).getId());
        return Response.buildSuccess(preferenceService.getUserPreferencesByCategory(userId, category));
    }

    /**
     * 保存或更新偏好
     */
    @PostMapping
    public Response<UserGlobalPreferenceVO> saveOrUpdatePreference(
            @RequestHeader("token") String token,
            @RequestBody UserGlobalPreferenceSaveRequest request) {
        String userId = String.valueOf(tokenUtil.getUser(token).getId());
        return Response.buildSuccess(preferenceService.saveOrUpdatePreference(userId, request));
    }

    /**
     * 批量保存偏好
     */
    @PostMapping("/batch")
    public Response<List<UserGlobalPreferenceVO>> batchSavePreferences(
            @RequestHeader("token") String token,
            @RequestBody List<UserGlobalPreferenceSaveRequest> requests) {
        String userId = String.valueOf(tokenUtil.getUser(token).getId());
        return Response.buildSuccess(preferenceService.batchSavePreferences(userId, requests));
    }

    /**
     * 删除偏好
     */
    @DeleteMapping("/{category}/{preferenceKey}")
    public Response<String> deletePreference(
            @RequestHeader("token") String token,
            @PathVariable String category,
            @PathVariable String preferenceKey) {
        String userId = String.valueOf(tokenUtil.getUser(token).getId());
        preferenceService.deletePreference(userId, category, preferenceKey);
        return Response.buildSuccess("删除成功");
    }

    /**
     * 切换偏好启用状态
     */
    @PutMapping("/{category}/{preferenceKey}/toggle")
    public Response<UserGlobalPreferenceVO> togglePreference(
            @RequestHeader("token") String token,
            @PathVariable String category,
            @PathVariable String preferenceKey,
            @RequestParam boolean enabled) {
        String userId = String.valueOf(tokenUtil.getUser(token).getId());
        return Response.buildSuccess(preferenceService.togglePreference(userId, category, preferenceKey, enabled));
    }

    /**
     * 获取所有可用的偏好类别定义
     */
    @GetMapping("/categories")
    public Response<Map<String, Object>> getPreferenceCategories() {
        return Response.buildSuccess(preferenceService.getPreferenceCategories());
    }
}