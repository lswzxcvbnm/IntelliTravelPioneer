package com.travel.intellitravel_pioneer.controller;

import com.travel.intellitravel_pioneer.service.UserService;
import com.travel.intellitravel_pioneer.vo.user.UserVO;
import com.travel.intellitravel_pioneer.vo.common.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @PostMapping
    public Response<String> register(@RequestBody UserVO userVO) {
        return Response.buildSuccess(userService.register(userVO, userVO.getPassword()));
    }

    @PostMapping("/login")
    public Response<String> login(@RequestBody UserVO userVO) {
        return Response.buildSuccess(userService.login(userVO.getUsername(), userVO.getPassword()));
    }

    @GetMapping("/{username}")
    public Response<UserVO> getInformation(@PathVariable String username) {
        return Response.buildSuccess(userService.getInformation(username));
    }

    @PutMapping
    public Response<String> updateInformation(@RequestBody UserVO userVO) {
        return Response.buildSuccess(userService.updateInformation(userVO));
    }

    @PostMapping("/{username}/experience")
    public Response<UserVO> addExperience(@PathVariable String username, @RequestBody UserVO userVO) {
        return Response.buildSuccess(userService.addExperience(username, userVO.getExperience()));
    }
}
