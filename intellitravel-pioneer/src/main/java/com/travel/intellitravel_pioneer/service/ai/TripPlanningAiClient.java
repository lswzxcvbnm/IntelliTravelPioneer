package com.travel.intellitravel_pioneer.service.ai;


import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryPlan;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryRequest;
import com.travel.intellitravel_pioneer.vo.trip.GenerationResult;

public interface TripPlanningAiClient {

    ItineraryPlan generateItinerary(String token, ItineraryRequest request);

    default GenerationResult generateItineraryWithStats(String token, ItineraryRequest request,
            java.util.function.Consumer<String> toolNotifier) {
        ItineraryPlan plan = generateItinerary(token, request);
        return new GenerationResult(plan, null);
    }

    ItineraryPlan modifyItinerary(String token, ItineraryRequest request,
            ItineraryPlan currentPlan, String userPrompt);

    default GenerationResult modifyItineraryWithStats(String token, ItineraryRequest request,
            ItineraryPlan currentPlan, String userPrompt,
            java.util.function.Consumer<String> toolNotifier) {
        ItineraryPlan plan = modifyItinerary(token, request, currentPlan, userPrompt);
        return new GenerationResult(plan, null);
    }
}
