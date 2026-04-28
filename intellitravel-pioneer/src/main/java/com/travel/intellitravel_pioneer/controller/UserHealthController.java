package com.travel.intellitravel_pioneer.controller;

import com.travel.intellitravel_pioneer.service.UserHealthService;
import com.travel.intellitravel_pioneer.vo.common.Response;
import com.travel.intellitravel_pioneer.vo.user.UserHealthVO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/health")
public class UserHealthController {

    @Autowired
    private UserHealthService userHealthService;

    @GetMapping("/{username}")
    public Response<UserHealthVO> getHealthInformation(@PathVariable String username) {
        return Response.buildSuccess(userHealthService.getHealthInformation(username));
    }

    @PostMapping("/create")
    public Response<String> createHealthInformation(@RequestBody UserHealthVO userHealthVO) {
        return Response.buildSuccess(userHealthService.saveOrUpdateHealthInformation(userHealthVO));
    }

    @PostMapping("/update")
    public Response<String> updateHealthInformation(@RequestBody UserHealthVO userHealthVO) {
        return Response.buildSuccess(userHealthService.saveOrUpdateHealthInformation(userHealthVO));
    }
}
