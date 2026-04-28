package com.travel.intellitravel_pioneer.service.ai;


import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryPlan;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryRequest;

public interface TripPlanningAiClient {

    ItineraryPlan generateItinerary(String token, ItineraryRequest request);

    default ItineraryPlan generateItinerary(String token, ItineraryRequest request,
            java.util.function.Consumer<String> toolNotifier) {
        return generateItinerary(token, request);
    }

    
}
