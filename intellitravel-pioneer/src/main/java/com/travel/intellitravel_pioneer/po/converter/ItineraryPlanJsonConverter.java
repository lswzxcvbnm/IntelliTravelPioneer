package com.travel.intellitravel_pioneer.po.converter;

import jakarta.persistence.Converter;

import com.travel.intellitravel_pioneer.util.JsonConverter;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryPlan;

@Converter(autoApply = true)
public class ItineraryPlanJsonConverter extends JsonConverter<ItineraryPlan> {

    public ItineraryPlanJsonConverter() {
        super(ItineraryPlan.class);
    }
}