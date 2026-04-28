package com.travel.intellitravel_pioneer.service;

import java.util.List;

import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryPlan;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryRequest;

import com.travel.intellitravel_pioneer.vo.itinerary.ItinerarySaveRequest;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryVO;

public interface ItineraryService {
    ItineraryPlan generateItinerary(String token, ItineraryRequest request);

    default ItineraryPlan generateItinerary(String token, ItineraryRequest request,
            java.util.function.Consumer<String> toolNotifier) {
        return generateItinerary(token, request);
    }

    String saveItinerary(String token, ItinerarySaveRequest request);

    void updateItinerary(String token, String itineraryId, ItinerarySaveRequest request);

    ItineraryVO getItineraryDetail(String token, String itineraryId);

    List<String> getAllItineraries(String token);

    List<String> getItinerariesByUserId(String userId);
    
    void deleteItinerary(String token, String itineraryId);
}