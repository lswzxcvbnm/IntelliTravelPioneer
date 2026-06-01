package com.travel.intellitravel_pioneer.controller;

import com.travel.intellitravel_pioneer.service.SkillService;
import com.travel.intellitravel_pioneer.util.TokenUtil;
import com.travel.intellitravel_pioneer.vo.common.Response;
import com.travel.intellitravel_pioneer.vo.skill.SkillSaveRequest;
import com.travel.intellitravel_pioneer.vo.skill.SkillVO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/skill")
public class SkillController {

    @Autowired
    private SkillService skillService;

    @Autowired
    private TokenUtil tokenUtil;

    @PostMapping
    public Response<SkillVO> createSkill(@RequestHeader("token") String token,
            @RequestBody SkillSaveRequest request) {
        String userId = String.valueOf(tokenUtil.getUser(token).getId());
        return Response.buildSuccess(skillService.createSkill(userId, request));
    }

    @PutMapping("/{skillId}")
    public Response<SkillVO> updateSkill(@RequestHeader("token") String token,
            @PathVariable String skillId,
            @RequestBody SkillSaveRequest request) {
        String userId = String.valueOf(tokenUtil.getUser(token).getId());
        return Response.buildSuccess(skillService.updateSkill(skillId, userId, request));
    }

    @DeleteMapping("/{skillId}")
    public Response<String> deleteSkill(@RequestHeader("token") String token,
            @PathVariable String skillId) {
        String userId = String.valueOf(tokenUtil.getUser(token).getId());
        skillService.deleteSkill(skillId, userId);
        return Response.buildSuccess("删除成功");
    }

    @GetMapping("/{skillId}")
    public Response<SkillVO> getSkill(@PathVariable String skillId) {
        return Response.buildSuccess(skillService.getSkill(skillId));
    }

    @GetMapping("/mine")
    public Response<List<SkillVO>> listMySkills(@RequestHeader("token") String token) {
        String userId = String.valueOf(tokenUtil.getUser(token).getId());
        return Response.buildSuccess(skillService.listMySkills(userId));
    }

    @GetMapping("/public")
    public Response<List<SkillVO>> listPublicSkills() {
        return Response.buildSuccess(skillService.listPublicSkills());
    }
}
