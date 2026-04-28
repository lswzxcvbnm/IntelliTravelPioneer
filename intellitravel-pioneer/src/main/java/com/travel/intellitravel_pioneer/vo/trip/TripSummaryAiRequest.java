package com.travel.intellitravel_pioneer.vo.trip;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class TripSummaryAiRequest {

    @JsonProperty("trip_id")
    private String tripId;

    @JsonProperty("itinerary_id")
    private String itineraryId;

    @JsonProperty("itinerary_title")
    private String itineraryTitle;

    private String destination;

    @JsonProperty("total_days")
    private Integer totalDays;

    @JsonProperty("planned_highlights")
    private List<String> plannedHighlights;

    @JsonProperty("itinerary_daily_outline")
    private List<String> itineraryDailyOutline;

    @JsonProperty("itinerary_raw")
    private String itineraryRaw;

    @JsonProperty("duration_minutes")
    private Integer durationMinutes;

    @JsonProperty("total_distance_meters")
    private Long totalDistanceMeters;

    @JsonProperty("trajectory_point_count")
    private Integer trajectoryPointCount;

    @JsonProperty("sampled_trajectory")
    private List<Location> sampledTrajectory;

    @JsonProperty("final_location_text")
    private String finalLocationText;

    @JsonProperty("nearby_pois")
    private List<String> nearbyPois;

    @JsonProperty("final_status")
    private UserRealTimeStatus finalStatus;
}