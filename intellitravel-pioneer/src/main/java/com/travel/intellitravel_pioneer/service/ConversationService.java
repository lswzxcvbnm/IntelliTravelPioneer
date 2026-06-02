package com.travel.intellitravel_pioneer.service;

import com.travel.intellitravel_pioneer.vo.conversation.*;

import java.util.List;

public interface ConversationService {

    ConversationVO createConversation(String token, ConversationCreateRequest request);

    List<ConversationVO> listConversations(String token, String keyword, String sortBy);

    ConversationDetailVO getConversationDetail(String token, String conversationId);

    ConversationVO updateConversation(String token, String conversationId, ConversationUpdateRequest request);

    void deleteConversation(String token, String conversationId);

    AgentMessageVO addMessage(String token, String conversationId, AgentMessageCreateRequest request);

    List<AgentMessageVO> getConversationMessages(String token, String conversationId);

    void linkItinerary(String token, String conversationId, String itineraryId);

    ConversationVO getConversationByItineraryId(String token, String itineraryId);
}
