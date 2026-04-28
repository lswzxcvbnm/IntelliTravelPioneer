package com.travel.intellitravel_pioneer.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "trip_summary")
public class TripSummaryPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "summary_id", nullable = false, unique = true, length = 64)
    private String summaryId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "trip_id", nullable = false, unique = true, length = 64)
    private String tripId;

    @Column(name = "itinerary_id", length = 64)
    private String itineraryId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "summary_text", nullable = false, columnDefinition = "TEXT")
    private String summaryText;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "total_distance_meters")
    private Long totalDistanceMeters;

    @Column(name = "trajectory_point_count")
    private Integer trajectoryPointCount;

    @Column(name = "final_steps")
    private Integer finalSteps;

    @Column(name = "final_current_power")
    private Integer finalCurrentPower;

    @Column(name = "final_total_power")
    private Integer finalTotalPower;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}