package com.travel.intellitravel_pioneer.vo.skill;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillSaveRequest {
    private String name;
    private String description;
    private String prompt;
    private Boolean isPublic;
}
