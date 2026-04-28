package com.travel.intellitravel_pioneer.vo.itinerary;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItineraryUpdateRequest {
    private String itineraryId;
    private String updateType;
    private ItineraryUpdateContent content;
}