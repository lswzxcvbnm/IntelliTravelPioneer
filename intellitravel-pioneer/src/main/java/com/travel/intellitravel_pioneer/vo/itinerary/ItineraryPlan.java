package com.travel.intellitravel_pioneer.vo.itinerary;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItineraryPlan {
    private String title;
    private Double estimatedCost;
    private Integer totalDays;
    private String destination;
    private List<DayPlan> dailyPlans;
}