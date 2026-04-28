package com.travel.intellitravel_pioneer.vo.trip;

import lombok.Data;

/**
 * 战力消耗计算数据
 */
@Data
public class PowerDetail {
    /**
     * 总步行距离（米）
     */
    private Double walkingDistance;
    /**
     * 预估平均心率
     */
    private Integer estimatedHeartRateAvg;
    /**
     * 预估最大心率
     */
    private Integer estimatedHeartRateMax;
    /**
     * 预估疲劳指数（0-100）
     */
    private Integer fatigueIndex;
}
