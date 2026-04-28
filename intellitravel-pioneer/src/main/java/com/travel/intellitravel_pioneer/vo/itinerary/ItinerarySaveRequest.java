package com.travel.intellitravel_pioneer.vo.itinerary;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
public class ItinerarySaveRequest {
    @JsonProperty("title")
    private String title;

    @JsonProperty("estimatedCost")
    private Double estimatedCost;

    @JsonProperty("totalDays")
    private Integer totalDays;

    @JsonProperty("destination")
    private String destination;

    @JsonProperty("dailyPlans")
    private List<DayPlan> dailyPlans;
}