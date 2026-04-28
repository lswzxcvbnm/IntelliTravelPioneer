package com.travel.intellitravel_pioneer.vo.trip;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class TripSummaryGenerationStatusResponse {

    @JsonProperty("trip_id")
    private String tripId;

    /**
     * GENERATED / IN_PROGRESS / FAILED / NOT_GENERATED
     */
    private String status;

    @JsonProperty("has_summary")
    private Boolean hasSummary;

    @JsonProperty("is_completed")
    private Boolean isCompleted;

    @JsonProperty("failure_message")
    private String failureMessage;

    @JsonProperty("last_updated_at")
    private OffsetDateTime lastUpdatedAt;
}