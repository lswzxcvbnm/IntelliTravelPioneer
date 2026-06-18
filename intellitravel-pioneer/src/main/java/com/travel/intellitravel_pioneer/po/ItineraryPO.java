package com.travel.intellitravel_pioneer.po;

import com.travel.intellitravel_pioneer.po.converter.GenerationStatsJsonConverter;
import com.travel.intellitravel_pioneer.po.converter.ItineraryPlanJsonConverter;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryPlan;
import com.travel.intellitravel_pioneer.vo.trip.GenerationStats;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "itinerary")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class ItineraryPO {

    @Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "conversation_id", length = 36)
    private String conversationId;

    @Column(name = "title", length = 255)
    private String title;

    @Convert(converter = ItineraryPlanJsonConverter.class)
    @Column(name = "plan", columnDefinition = "TEXT")
    private ItineraryPlan plan;

    @Convert(converter = GenerationStatsJsonConverter.class)
    @Column(name = "stats", columnDefinition = "TEXT")
    private GenerationStats stats;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
