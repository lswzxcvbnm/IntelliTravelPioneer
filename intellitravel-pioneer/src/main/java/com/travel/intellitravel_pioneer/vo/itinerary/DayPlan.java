package com.travel.intellitravel_pioneer.vo.itinerary;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DayPlan {
    @JsonProperty("dayNumber")
    private Integer dayNumber;

    @JsonProperty("items")
    private List<RouteItem> items;
}