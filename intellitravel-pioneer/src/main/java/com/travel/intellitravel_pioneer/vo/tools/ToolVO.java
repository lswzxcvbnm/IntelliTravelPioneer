package com.travel.intellitravel_pioneer.vo.tools;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolVO {
    private Long id;
    private String toolName;
    private String displayName;
    private String description;
    private String category;
    private String parametersJson;
    private String usageExample;
    private Boolean enabled;
    private Boolean userEnabled;
    private Integer sortOrder;
    private Long totalCalls;
    private Long successCalls;
}
