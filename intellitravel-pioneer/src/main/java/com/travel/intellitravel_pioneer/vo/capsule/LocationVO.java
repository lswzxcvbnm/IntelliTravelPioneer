package com.travel.intellitravel_pioneer.vo.capsule;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationVO {
    private String name;
    private String address;
    private Double lat; // 高德纬度
    private Double lng; // 高德经度
    private String city;
    private String adcode;
    private String country;
}