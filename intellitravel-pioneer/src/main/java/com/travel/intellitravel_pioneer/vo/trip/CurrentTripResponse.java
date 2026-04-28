package com.travel.intellitravel_pioneer.vo.trip;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CurrentTripResponse {

    @JsonProperty("has_active_trip")
    private Boolean hasActiveTrip;

    @JsonProperty("trip_id")
    private String tripId;

    @JsonProperty("itinerary_id")
    private String itineraryId;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;
}
