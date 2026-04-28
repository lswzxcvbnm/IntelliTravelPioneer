package com.travel.intellitravel_pioneer.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * 调整建议类型枚举
 */
public enum SuggestionType {
    ROUTE_CHANGE("路线变更"),
    REST_REMIND("休息提醒"),
    POWER_WARNING("战力预警");

    private final String desc;

    SuggestionType(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    @JsonCreator
    public static SuggestionType fromValue(String value) {
        if (value == null) {
            return null;
        }
        return SuggestionType.valueOf(value.trim().toUpperCase());
    }
}
