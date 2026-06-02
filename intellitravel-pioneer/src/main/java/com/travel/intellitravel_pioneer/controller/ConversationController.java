package com.travel.intellitravel_pioneer.controller;

import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import com.travel.intellitravel_pioneer.po.UserPO;
import com.travel.intellitravel_pioneer.service.ConversationService;
import com.travel.intellitravel_pioneer.util.TokenUtil;
import com.travel.intellitravel_pioneer.vo.common.Response;
import com.travel.intellitravel_pioneer.vo.conversation.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversation")
public class ConversationController {

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private TokenUtil tokenUtil;

    private String getUserIdFromToken(String token) {
        if (token == null || token.isEmpty()) {
            throw new IntellitravelException("Token is missing");
        }
        UserPO user = tokenUtil.getUser(token);
        if (user == null) {
            throw new IntellitravelException("Invalid token");
        }
        return String.valueOf(user.getId());
    }

    @PostMapping("/create")
    public Response<ConversationVO> createConversation(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "token", required = false) String tokenQuery,
            @RequestBody(required = false) ConversationCreateRequest request) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        ConversationCreateRequest req = request != null ? request : new ConversationCreateRequest();
        ConversationVO result = conversationService.createConversation(effectiveToken, req);
        return Response.buildSuccess(result);
    }

    @GetMapping("/list")
    public Response<List<ConversationVO>> listConversations(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "token", required = false) String tokenQuery,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sortBy", required = false) String sortBy) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        List<ConversationVO> result = conversationService.listConversations(effectiveToken, keyword, sortBy);
        return Response.buildSuccess(result);
    }

    @GetMapping("/detail/{conversationId}")
    public Response<ConversationDetailVO> getConversationDetail(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "token", required = false) String tokenQuery,
            @PathVariable String conversationId) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        ConversationDetailVO result = conversationService.getConversationDetail(effectiveToken, conversationId);
        return Response.buildSuccess(result);
    }

    @GetMapping("/messages/{conversationId}")
    public Response<List<AgentMessageVO>> getConversationMessages(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "token", required = false) String tokenQuery,
            @PathVariable String conversationId) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        List<AgentMessageVO> result = conversationService.getConversationMessages(effectiveToken, conversationId);
        return Response.buildSuccess(result);
    }

    @PostMapping("/message/{conversationId}")
    public Response<AgentMessageVO> addMessage(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "token", required = false) String tokenQuery,
            @PathVariable String conversationId,
            @RequestBody AgentMessageCreateRequest request) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        AgentMessageVO result = conversationService.addMessage(effectiveToken, conversationId, request);
        return Response.buildSuccess(result);
    }

    @PutMapping("/{conversationId}")
    public Response<ConversationVO> updateConversation(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "token", required = false) String tokenQuery,
            @PathVariable String conversationId,
            @RequestBody ConversationUpdateRequest request) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        ConversationVO result = conversationService.updateConversation(effectiveToken, conversationId, request);
        return Response.buildSuccess(result);
    }

    @DeleteMapping("/{conversationId}")
    public Response<String> deleteConversation(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "token", required = false) String tokenQuery,
            @PathVariable String conversationId) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        conversationService.deleteConversation(effectiveToken, conversationId);
        return Response.buildSuccess("Conversation deleted successfully");
    }

    @PutMapping("/{conversationId}/link-itinerary")
    public Response<String> linkItinerary(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "token", required = false) String tokenQuery,
            @PathVariable String conversationId,
            @RequestParam String itineraryId) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        conversationService.linkItinerary(effectiveToken, conversationId, itineraryId);
        return Response.buildSuccess("Conversation linked to itinerary");
    }

    @GetMapping("/by-itinerary/{itineraryId}")
    public Response<ConversationVO> getConversationByItineraryId(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "token", required = false) String tokenQuery,
            @PathVariable String itineraryId) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        ConversationVO result = conversationService.getConversationByItineraryId(effectiveToken, itineraryId);
        return Response.buildSuccess(result);
    }
}
