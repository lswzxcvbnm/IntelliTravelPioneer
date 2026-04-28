package com.travel.intellitravel_pioneer.vo.trip;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 地理坐标对象
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Location {
    /**
     * 纬度
     */
    private Double lat;
    /**
     * 经度
     */
    private Double lng;
    /**
     * 地点名称（可选）
     */
    private String name;
}
