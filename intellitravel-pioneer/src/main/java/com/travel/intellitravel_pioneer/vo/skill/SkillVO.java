package com.travel.intellitravel_pioneer.vo.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillVO {
    private String id;
    private String userId;
    private String name;
    private String description;
    private String prompt;
    private Map<String, String> references;
    private Map<String, String> scripts;
    private Boolean isPublic;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
