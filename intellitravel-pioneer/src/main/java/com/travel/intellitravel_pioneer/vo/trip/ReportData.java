package com.travel.intellitravel_pioneer.vo.trip;

import lombok.Data;

@Data
public class ReportData {
    private String trajectoryUrl;
    private Integer steps;
    private Integer powerCost;
    private Integer durationMin;
    private String theme; // "nature", "city", "art", "retro"
}
