package com.travel.intellitravel_pioneer.po;

import lombok.Data;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Data
@Entity
@Table(name = "trip_report")
public class TripReportPO {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "trip_id")
    private String tripId;

    @Column(name = "report_url")
    private String reportImageUrl;

    @Column(name = "template_id")
    private String templateId;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;
}
