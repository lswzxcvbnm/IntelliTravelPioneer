package com.travel.intellitravel_pioneer.service;

import com.travel.intellitravel_pioneer.vo.skill.SkillSaveRequest;
import com.travel.intellitravel_pioneer.vo.skill.SkillVO;

import java.util.List;

public interface SkillService {
    SkillVO createSkill(String userId, SkillSaveRequest request);
    SkillVO updateSkill(String skillId, String userId, SkillSaveRequest request);
    void deleteSkill(String skillId, String userId);
    SkillVO getSkill(String skillId);
    List<SkillVO> listMySkills(String userId);
    List<SkillVO> listPublicSkills();
    String buildSkillContext(List<String> skillIds);
}
