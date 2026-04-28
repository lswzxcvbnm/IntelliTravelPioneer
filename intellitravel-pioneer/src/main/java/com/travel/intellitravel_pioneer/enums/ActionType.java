package com.travel.intellitravel_pioneer.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * 行动指令类型枚举
 */
public enum ActionType {
    NAVIGATE("导航至目标地点"),
    REST("原地休息"),
    SKIP("跳过当前景点");

    private final String desc;

    ActionType(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    @JsonCreator
    public static ActionType fromValue(String value) {
        if (value == null) {
            return null;
        }
        return ActionType.valueOf(value.trim().toUpperCase());
    }
}
