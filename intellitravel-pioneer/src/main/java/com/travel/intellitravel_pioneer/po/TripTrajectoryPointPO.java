package com.travel.intellitravel_pioneer.po;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "trip_trajectory_point", indexes = {
        @Index(name = "idx_trip_trajectory_trip_seq", columnList = "trip_id,seq"),
        @Index(name = "idx_trip_trajectory_trip_time", columnList = "trip_id,sampled_at")
})
public class TripTrajectoryPointPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trip_id", nullable = false, length = 64)
    private String tripId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "seq", nullable = false)
    private Integer seq;

    @Column(name = "lng", nullable = false)
    private Double lng;

    @Column(name = "lat", nullable = false)
    private Double lat;

    @Column(name = "sampled_at", nullable = false)
    private OffsetDateTime sampledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
