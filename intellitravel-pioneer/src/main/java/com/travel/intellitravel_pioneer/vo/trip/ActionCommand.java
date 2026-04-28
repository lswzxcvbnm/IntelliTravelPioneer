package com.travel.intellitravel_pioneer.vo.trip;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import com.travel.intellitravel_pioneer.enums.ActionType;

/**
 * 行动指令详情
 */
@Data
public class ActionCommand {
    /**
     * 指令类型
     */
    private ActionType type;
    /**
     * 目标位置（仅导航类型有效）
     */
    private Location target;
    /**
     * 建议时长（如休息10分钟，单位：分钟）
     */
    @JsonProperty("duration_minutes")
    private Integer durationMinutes;
}
