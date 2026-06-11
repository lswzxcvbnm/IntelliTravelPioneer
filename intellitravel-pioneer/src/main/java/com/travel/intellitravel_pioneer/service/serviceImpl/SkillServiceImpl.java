package com.travel.intellitravel_pioneer.service.serviceImpl;

import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import com.travel.intellitravel_pioneer.po.SkillPO;
import com.travel.intellitravel_pioneer.repository.SkillRepository;
import com.travel.intellitravel_pioneer.service.SkillService;
import com.travel.intellitravel_pioneer.vo.skill.SkillSaveRequest;
import com.travel.intellitravel_pioneer.vo.skill.SkillVO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SkillServiceImpl implements SkillService {

    @Autowired
    private SkillRepository skillRepository;

    @Override
    public SkillVO createSkill(String userId, SkillSaveRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IntellitravelException("技能名称不能为空");
        }
        if (request.getPrompt() == null || request.getPrompt().isBlank()) {
            throw new IntellitravelException("技能提示词不能为空");
        }
        SkillPO po = SkillPO.builder()
                .userId(userId)
                .name(request.getName())
                .description(request.getDescription())
                .prompt(request.getPrompt())
                .isPublic(request.getIsPublic() != null ? request.getIsPublic() : false)
                .build();
        po = skillRepository.save(po);
        return toVO(po);
    }

    @Override
    public SkillVO updateSkill(String skillId, String userId, SkillSaveRequest request) {
        SkillPO po = skillRepository.findById(skillId)
                .orElseThrow(() -> new IntellitravelException("技能不存在"));
        if (!po.getUserId().equals(userId)) {
            throw new IntellitravelException("无权修改此技能");
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            po.setName(request.getName());
        }
        if (request.getDescription() != null) {
            po.setDescription(request.getDescription());
        }
        if (request.getPrompt() != null && !request.getPrompt().isBlank()) {
            po.setPrompt(request.getPrompt());
        }
        if (request.getIsPublic() != null) {
            po.setIsPublic(request.getIsPublic());
        }
        po = skillRepository.save(po);
        return toVO(po);
    }

    @Override
    public void deleteSkill(String skillId, String userId) {
        SkillPO po = skillRepository.findById(skillId)
                .orElseThrow(() -> new IntellitravelException("技能不存在"));
        if (!po.getUserId().equals(userId)) {
            throw new IntellitravelException("无权删除此技能");
        }
        skillRepository.delete(po);
    }

    @Override
    public SkillVO getSkill(String skillId) {
        SkillPO po = skillRepository.findById(skillId)
                .orElseThrow(() -> new IntellitravelException("技能不存在"));
        return toVO(po);
    }

    @Override
    public List<SkillVO> listMySkills(String userId) {
        return skillRepository.findByUserId(userId).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<SkillVO> listPublicSkills() {
        return skillRepository.findByIsPublicTrue().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public String buildSkillContext(List<String> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return "";
        }
        List<SkillPO> skills = skillRepository.findAllById(skillIds);
        if (skills.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n## 用户启用的专属技能\n");
        sb.append("以下是用户定义的特殊技能/偏好，请在行程规划中严格遵循：\n\n");
        for (SkillPO skill : skills) {
            sb.append("### ").append(skill.getName()).append("\n");
            if (skill.getDescription() != null && !skill.getDescription().isBlank()) {
                sb.append("描述：").append(skill.getDescription()).append("\n");
            }
            sb.append("要求：").append(skill.getPrompt()).append("\n\n");
        }
        return sb.toString();
    }

    private SkillVO toVO(SkillPO po) {
        return SkillVO.builder()
                .id(po.getId())
                .userId(po.getUserId())
                .name(po.getName())
                .description(po.getDescription())
                .prompt(po.getPrompt())
                .isPublic(po.getIsPublic())
                .createTime(po.getCreateTime())
                .updateTime(po.getUpdateTime())
                .build();
    }
}
