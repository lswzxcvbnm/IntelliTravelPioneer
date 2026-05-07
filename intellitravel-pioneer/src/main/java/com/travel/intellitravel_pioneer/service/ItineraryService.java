package com.travel.intellitravel_pioneer.service;

import java.util.List;

import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryPlan;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryRequest;

import com.travel.intellitravel_pioneer.vo.itinerary.ItinerarySaveRequest;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryVO;
import com.travel.intellitravel_pioneer.vo.trip.GenerationResult;

public interface ItineraryService {
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

    String saveItinerary(String token, ItinerarySaveRequest request);

    void updateItinerary(String token, String itineraryId, ItinerarySaveRequest request);

    ItineraryVO getItineraryDetail(String token, String itineraryId);

    List<String> getAllItineraries(String token);

    List<String> getItinerariesByUserId(String userId);
    
    void deleteItinerary(String token, String itineraryId);
}
