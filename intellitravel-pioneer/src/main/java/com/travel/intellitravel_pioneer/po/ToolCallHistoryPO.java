package com.travel.intellitravel_pioneer.po;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tool_call_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolCallHistoryPO {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "tool_name", length = 100, nullable = false)
    private String toolName;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "itinerary_id", length = 36)
    private String itineraryId;

    @Column(name = "parameters", columnDefinition = "TEXT")
    private String parameters;

    @Column(name = "result_summary", length = 500)
    private String resultSummary;

    @Column(name = "success", nullable = false)
    @Builder.Default
    private Boolean success = true;

    @Column(name = "call_time")
    @Builder.Default
    private LocalDateTime callTime = LocalDateTime.now();
}
