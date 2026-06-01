package com.travel.intellitravel_pioneer.controller;

import com.travel.intellitravel_pioneer.service.ImageService;
import com.travel.intellitravel_pioneer.service.ToolManagementService;
import com.travel.intellitravel_pioneer.util.TokenUtil;
import com.travel.intellitravel_pioneer.vo.common.Response;
import com.travel.intellitravel_pioneer.vo.tools.ToolCallHistoryVO;
import com.travel.intellitravel_pioneer.vo.tools.ToolVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/tools")
public class ToolsController {

    @Autowired
    private ImageService imageService;

    @Autowired
    private ToolManagementService toolManagementService;

    @Autowired
    private TokenUtil tokenUtil;

    @PostMapping("/images")
    public Response<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String url = imageService.upload(file);
        Map<String, String> res = new HashMap<>();
        res.put("url", url);
        return Response.buildSuccess(res);
    }

    @GetMapping("/list")
    public Response<List<ToolVO>> listTools(@RequestHeader("token") String token) {
        String userId = String.valueOf(tokenUtil.getUser(token).getId());
        List<ToolVO> tools = toolManagementService.getAllTools(userId);
        return Response.buildSuccess(tools);
    }

    @GetMapping("/{toolName}")
    public Response<ToolVO> getToolDetail(
            @RequestHeader("token") String token,
            @PathVariable("toolName") String toolName) {
        String userId = String.valueOf(tokenUtil.getUser(token).getId());
        ToolVO tool = toolManagementService.getToolDetail(userId, toolName);
        return Response.buildSuccess(tool);
    }

    @PutMapping("/{toolName}/toggle")
    public Response<ToolVO> toggleTool(
            @RequestHeader("token") String token,
            @PathVariable("toolName") String toolName) {
        String userId = String.valueOf(tokenUtil.getUser(token).getId());
        ToolVO tool = toolManagementService.toggleUserPreference(userId, toolName);
        return Response.buildSuccess(tool);
    }

    @GetMapping("/calls")
    public Response<Map<String, Object>> getCallHistory(
            @RequestHeader("token") String token,
            @RequestParam(value = "toolName", required = false) String toolName,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        String userId = String.valueOf(tokenUtil.getUser(token).getId());
        Page<ToolCallHistoryVO> result = toolManagementService.getToolCallHistory(userId, toolName, page, size);
        Map<String, Object> data = new HashMap<>();
        data.put("content", result.getContent());
        data.put("totalElements", result.getTotalElements());
        data.put("totalPages", result.getTotalPages());
        data.put("currentPage", result.getNumber());
        data.put("pageSize", result.getSize());
        return Response.buildSuccess(data);
    }
}