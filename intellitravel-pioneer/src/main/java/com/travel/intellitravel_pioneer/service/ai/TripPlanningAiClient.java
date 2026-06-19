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
            ItineraryPlan currentPlan, String userPrompt,String conversationId);

    default GenerationResult modifyItineraryWithStats(String token, ItineraryRequest request,
            ItineraryPlan currentPlan, String userPrompt,
            java.util.function.Consumer<String> toolNotifier,String conversationId) {
        ItineraryPlan plan = modifyItinerary(token, request, currentPlan, userPrompt ,conversationId);
        return new GenerationResult(plan, null);
    }

    /**
     * AI-backed intent classification: CHAT or MODIFY.
     * Uses LLM to classify user intent for modification requests.
     */
    default String classifyIntent(String userMessage) {
        return "CHAT"; // default implementation, overridden by HttpTripPlanningAiClient
    }

    /** Generate a brief chat answer for non-modify questions in refine panel */
    default String answerChatQuestion(String userMessage, String currentPlanJson, String token) {
        return "如需修改行程，请明确说明如何调整（如\"把第一天换成室内景点\"）。";
    }
}
