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
    /**
     * Build skill context for injection into LLM system/user messages.
     * <p>
     * Follows Anthropic Skill progressive-disclosure model:
     * <ol>
     *   <li>Skill name + description (always injected)</li>
     *   <li>Prompt (SKILL.md body) — required instructions</li>
     *   <li>References — bundled knowledge docs loaded on demand</li>
     *   <li>Scripts — bundled executable scripts (may be referenced by Agent)</li>
     * </ol>
     *
     * @param skillIds list of skill IDs to include
     * @return formatted skill context string, or empty string if no skills
     */
    String buildSkillContext(List<String> skillIds);
}
