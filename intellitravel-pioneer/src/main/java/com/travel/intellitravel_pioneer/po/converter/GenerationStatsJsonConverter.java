package com.travel.intellitravel_pioneer.po.converter;

import jakarta.persistence.Converter;

import com.travel.intellitravel_pioneer.util.JsonConverter;
import com.travel.intellitravel_pioneer.vo.trip.GenerationStats;

@Converter(autoApply = true)
public class GenerationStatsJsonConverter extends JsonConverter<GenerationStats> {

    public GenerationStatsJsonConverter() {
        super(GenerationStats.class);
    }
}
