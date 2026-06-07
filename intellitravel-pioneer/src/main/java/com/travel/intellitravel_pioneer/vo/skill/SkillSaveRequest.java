package com.travel.intellitravel_pioneer.vo.skill;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillSaveRequest {
    private String name;
    private String description;
    private String prompt;
    private Map<String, String> references;
    private Map<String, String> scripts;
    private Boolean isPublic;
}
