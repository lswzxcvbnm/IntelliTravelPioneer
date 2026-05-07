package com.travel.intellitravel_pioneer.vo.trip;

import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryPlan;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerationResult {

    private ItineraryPlan plan;
    private GenerationStats stats;
}
