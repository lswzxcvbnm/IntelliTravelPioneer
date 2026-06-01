package com.travel.intellitravel_pioneer.vo.tools;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolCallHistoryVO {
    private Long id;
    private String toolName;
    private String displayName;
    private String itineraryId;
    private String parameters;
    private String resultSummary;
    private Boolean success;
    private LocalDateTime callTime;
}
