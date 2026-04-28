package com.travel.intellitravel_pioneer.vo.itinerary;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItineraryUpdateContent {
    private List<DayPlan> dailyPlans;
}