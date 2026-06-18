package com.travel.intellitravel_pioneer.vo.itinerary;
import com.travel.intellitravel_pioneer.vo.trip.GenerationStats;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItineraryVO {
    private String id;
    private String userId;
    private String conversationId;
    private String title;
    private String destination;
    private ItineraryPlan plan;
    private GenerationStats stats;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
