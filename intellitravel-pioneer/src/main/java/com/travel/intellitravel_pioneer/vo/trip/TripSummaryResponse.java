package com.travel.intellitravel_pioneer.vo.trip;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class TripSummaryResponse {

    @JsonProperty("summary_id")
    private String summaryId;

    @JsonProperty("trip_id")
    private String tripId;

    @JsonProperty("itinerary_id")
    private String itineraryId;

    private String title;

    private String summary;

    @JsonProperty("duration_minutes")
    private Integer durationMinutes;

    @JsonProperty("total_distance_meters")
    private Long totalDistanceMeters;

    @JsonProperty("trajectory_point_count")
    private Integer trajectoryPointCount;

    @JsonProperty("final_steps")
    private Integer finalSteps;

    @JsonProperty("final_current_power")
    private Integer finalCurrentPower;

    @JsonProperty("final_total_power")
    private Integer finalTotalPower;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @JsonProperty("updated_at")
    private OffsetDateTime updatedAt;
}