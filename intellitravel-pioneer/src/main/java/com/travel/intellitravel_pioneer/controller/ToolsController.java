package com.travel.intellitravel_pioneer.controller;

import com.travel.intellitravel_pioneer.service.ImageService;
import com.travel.intellitravel_pioneer.vo.common.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("api/tools")
public class ToolsController {

    @Autowired
    private ImageService imageService;

    @PostMapping("/images")
    public Response<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        // 调用 ImageService 处理上传并返回 URL
        String url = imageService.upload(file);
        Map<String, String> res = new HashMap<>();
        res.put("url", url);
        return Response.buildSuccess(res);
    }
}