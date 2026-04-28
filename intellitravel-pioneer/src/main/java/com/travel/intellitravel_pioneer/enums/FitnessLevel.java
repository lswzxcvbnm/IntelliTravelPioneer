package com.travel.intellitravel_pioneer.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * 体能等级枚举
 */
public enum FitnessLevel {
    LOW("低体能"),
    MEDIUM("中等体能"),
    HIGH("高体能");

    private final String desc;

    FitnessLevel(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    @JsonCreator
    public static FitnessLevel fromValue(String value) {
        if (value == null) {
            return null;
        }
        return FitnessLevel.valueOf(value.trim().toUpperCase());
    }
}
