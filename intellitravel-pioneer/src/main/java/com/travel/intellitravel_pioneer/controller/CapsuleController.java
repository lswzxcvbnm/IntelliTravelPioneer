package com.travel.intellitravel_pioneer.controller;

import com.travel.intellitravel_pioneer.service.CapsuleService;
import com.travel.intellitravel_pioneer.vo.capsule.CapsuleDetailVO;
import com.travel.intellitravel_pioneer.vo.capsule.CapsuleListItemVO;
import com.travel.intellitravel_pioneer.vo.common.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/capsule")
public class CapsuleController {

    @Autowired
    private CapsuleService capsuleService;

    // 为了简化演示，暂时用RequestParam传入当前操作的userId。
    // 在真实应用中，这通常会通过拦截器、JWT Token或者Session中获取。

    @PostMapping("/create")
    public Response<String> createCapsule(
            @RequestParam Long userId,
            @RequestBody CapsuleDetailVO capsuleDetail) {
        return Response.buildSuccess(capsuleService.createCapsule(userId, capsuleDetail));
    }

    @DeleteMapping("/{capsuleId}")
    public Response<Void> deleteCapsule(
            @PathVariable Long capsuleId,
            @RequestParam Long userId) {
        capsuleService.deleteCapsule(capsuleId, userId);
        return Response.buildSuccess(null);
    }

    @PostMapping("/{capsuleId}/update")
    public Response<Void> updateCapsule(
            @PathVariable Long capsuleId,
            @RequestParam Long userId,
            @RequestBody CapsuleDetailVO capsuleDetail) {
        capsuleService.updateCapsule(capsuleId, userId, capsuleDetail);
        return Response.buildSuccess(null);
    }

    @GetMapping("/user/{userId}")
    public Response<List<CapsuleListItemVO>> getUserCapsules(@PathVariable Long userId) {
        return Response.buildSuccess(capsuleService.getUserCapsules(userId));
    }

    @GetMapping("/location")
    public Response<List<CapsuleListItemVO>> getCapsulesByLocation(
            @RequestParam String locationName,
            @RequestParam(required = false) String city) {
        return Response.buildSuccess(capsuleService.getCapsulesByLocation(locationName, city));
    }

    @GetMapping("/location/nearby")
    public Response<List<CapsuleListItemVO>> getCapsulesByLocation(
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(required = false, defaultValue = "100") Double radiusMeters) {
        return Response.buildSuccess(capsuleService.getCapsulesByLocation(latitude, longitude, radiusMeters));
    }

    @GetMapping("/{capsuleId}/detail")
    public Response<CapsuleDetailVO> getCapsuleDetail(
            @PathVariable Long capsuleId,
            @RequestParam(required = false) Long currentUserId) {
        return Response.buildSuccess(capsuleService.getCapsuleDetail(capsuleId, currentUserId));
    }
}
