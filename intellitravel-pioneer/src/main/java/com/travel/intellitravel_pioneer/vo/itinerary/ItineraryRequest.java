package com.travel.intellitravel_pioneer.vo.itinerary;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItineraryRequest {
    private String destination;
    private Integer days;
    private List<String> interests;
    private String budgetLevel;
    private String additionalNotes;
    private List<String> skillIds;
}