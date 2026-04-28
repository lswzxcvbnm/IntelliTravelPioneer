package com.travel.intellitravel_pioneer.vo.trip;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.time.OffsetDateTime;

/**
 * 用户实时状态（传感器数据）上报对象
 * 对应接口：/api/trips/{trip_id}/status
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserRealTimeStatus {
    /**
     * 行程ID（后端生成）
     */
    @JsonProperty("trip_id")
    private String tripId;

    /**
     * 上报时间戳（ISO 8601）
     */
    private OffsetDateTime timestamp;
    /**
     * 当前心率
     */
    @JsonProperty("heart_rate")
    private Integer heartRate;
    /**
     * 当日累计步数
     */
    private Integer steps;
    /**
     * 当前位置
     */
    private Location location;
    /**
     * 本次上报战力消耗
     */
    @JsonProperty("power_cost")
    private Integer powerCost;
    /**
     * 当前战力值（后端可根据实时数据计算后回填）
     */
    @JsonProperty("current_power")
    private Integer currentPower;
    /**
     * 总战力值（每次开始行程时初始化）
     */
    @JsonProperty("total_power")
    private Integer totalPower;
    /**
     * 战力计算明细
     */
    @JsonProperty("power_detail")
    private PowerDetail powerDetail;
    /**
     * 扩展字段（如血氧、体温等）
     */
    private String extInfo;
    /**
     * 是否强制刷新轨迹缓冲区（用于退出导航时确保轨迹点写入数据库）
     */
    @JsonProperty("flush_buffer")
    private Boolean flushBuffer;

    /**
     * 是否使用前端计算的战力消耗
     * true: 前端无真实传感器数据，前端已计算战力，后端跳过计算
     * false: 前端有真实传感器数据，后端使用后端逻辑计算
     */
    @JsonProperty("use_frontend_power_calculation")
    private Boolean useFrontendPowerCalculation;
}
