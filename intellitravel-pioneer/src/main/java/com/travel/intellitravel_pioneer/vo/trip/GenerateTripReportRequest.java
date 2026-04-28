package com.travel.intellitravel_pioneer.vo.trip;

import lombok.Data;

@Data
public class GenerateTripReportRequest {

    /**
     * 风格：可选值为："nature", "city", "art", "retro"
     */
    private String style;
}