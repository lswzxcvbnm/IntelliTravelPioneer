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

    /** Check if message contains explicit modify keywords */
    default boolean isModifyLike(String text) {
        if (text == null) return false;
        return text.contains("改成") || text.contains("换成") || text.contains("删除") ||
                text.contains("去掉") || text.contains("增加") || text.contains("添加") ||
                text.contains("调整") || text.contains("修改") || text.contains("替换") ||
                text.contains("加一个") || text.contains("删掉") || text.contains("改一下") ||
                text.contains("放到") || text.contains("挪到") || text.contains("移到") ||
                text.contains("加入") || text.contains("新增") || text.contains("改进") ||
                text.contains("优化") || text.contains("改一改") || text.contains("完善") ||
                text.contains("多玩") || text.contains("少玩") || text.contains("多一天") ||
                text.contains("少一天") || text.contains("加一天") || text.contains("减一天") ||
                text.contains("多加") || text.contains("减少") || text.contains("延长") ||
                text.contains("缩短") || text.contains("再多") || text.contains("少一点");
    }

    /**
     * AI-backed intent classification: CHAT or MODIFY.
     * Used as fallback when keyword matching fails.
     */
    default String classifyIntent(String userMessage) {
        return "CHAT"; // default: no AI, just keyword-dependent
    }

    /** Generate a brief chat answer for non-modify questions in refine panel */
    default String answerChatQuestion(String userMessage, String currentPlanJson) {
        return "如需修改行程，请明确说明如何调整（如\"把第一天换成室内景点\"）。";
    }
}
