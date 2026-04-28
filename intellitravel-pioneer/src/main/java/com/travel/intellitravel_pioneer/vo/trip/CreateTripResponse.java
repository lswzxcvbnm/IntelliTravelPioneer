package com.travel.intellitravel_pioneer.vo.trip;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CreateTripResponse {

    @JsonProperty("trip_id")
    private String tripId;

    private String message;
}
