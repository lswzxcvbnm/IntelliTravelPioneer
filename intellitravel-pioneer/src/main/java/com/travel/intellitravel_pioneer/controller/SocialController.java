package com.travel.intellitravel_pioneer.controller;

import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import com.travel.intellitravel_pioneer.po.UserPO;
import com.travel.intellitravel_pioneer.service.SocialService;
import com.travel.intellitravel_pioneer.util.TokenUtil;
import com.travel.intellitravel_pioneer.vo.common.Response;
import com.travel.intellitravel_pioneer.vo.social.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/social")
public class SocialController {

    @Autowired
    private SocialService socialService;

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

    @PostMapping("/follow/{targetUserId}")
    public Response<FollowResultVO> followUser(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "token", required = false) String tokenQuery,
            @PathVariable String targetUserId) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        String currentUserId = getUserIdFromToken(effectiveToken);
        FollowResultVO result = socialService.followUser(currentUserId, targetUserId);
        return Response.buildSuccess(result);
    }

    @PostMapping("/unfollow/{targetUserId}")
    public Response<FollowResultVO> unfollowUser(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "token", required = false) String tokenQuery,
            @PathVariable String targetUserId) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        String currentUserId = getUserIdFromToken(effectiveToken);
        FollowResultVO result = socialService.unfollowUser(currentUserId, targetUserId);
        return Response.buildSuccess(result);
    }

    @GetMapping("/following/{userId}")
    public Response<List<UserRelationVO>> getFollowingList(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "token", required = false) String tokenQuery,
            @PathVariable String userId) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        String currentUserId = getUserIdFromToken(effectiveToken);
        List<UserRelationVO> list = socialService.getFollowingList(userId, currentUserId);
        return Response.buildSuccess(list);
    }

    @GetMapping("/followers/{userId}")
    public Response<List<UserRelationVO>> getFollowerList(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "token", required = false) String tokenQuery,
            @PathVariable String userId) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        String currentUserId = getUserIdFromToken(effectiveToken);
        List<UserRelationVO> list = socialService.getFollowerList(userId, currentUserId);
        return Response.buildSuccess(list);
    }

    @GetMapping("/friends")
    public Response<List<UserRelationVO>> getFriendList(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "token", required = false) String tokenQuery) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        String userId = getUserIdFromToken(effectiveToken);
        List<UserRelationVO> list = socialService.getFriendList(userId);
        return Response.buildSuccess(list);
    }

    @GetMapping("/friends/{userId}")
    public Response<List<UserRelationVO>> getUserFriendList(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "token", required = false) String tokenQuery,
            @PathVariable String userId) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        getUserIdFromToken(effectiveToken);
        List<UserRelationVO> list = socialService.getFriendList(userId);
        return Response.buildSuccess(list);
    }

    @PostMapping("/messages")
    public Response<MessageVO> sendMessage(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "token", required = false) String tokenQuery,
            @RequestBody MessageSendRequest request) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        String senderId = getUserIdFromToken(effectiveToken);
        MessageVO message = socialService.sendMessage(senderId, request.getReceiverId(), request.getContent(), request.getShareType(), request.getShareId());
        return Response.buildSuccess(message);
    }

    @GetMapping("/messages/{friendId}")
    public Response<Page<MessageVO>> getConversationMessages(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "token", required = false) String tokenQuery,
            @PathVariable String friendId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        String userId = getUserIdFromToken(effectiveToken);
        Page<MessageVO> messages = socialService.getConversationMessages(userId, friendId, page, size);
        return Response.buildSuccess(messages);
    }

    @GetMapping("/conversations")
    public Response<List<ConversationVO>> getConversations(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "token", required = false) String tokenQuery) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        String userId = getUserIdFromToken(effectiveToken);
        List<ConversationVO> conversations = socialService.getConversations(userId);
        return Response.buildSuccess(conversations);
    }

    @PutMapping("/messages/read/{friendId}")
    public Response<Void> markMessagesAsRead(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "token", required = false) String tokenQuery,
            @PathVariable String friendId) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        String userId = getUserIdFromToken(effectiveToken);
        socialService.markMessagesAsRead(userId, friendId);
        return Response.buildSuccess(null);
    }

    @GetMapping("/messages/unread-count")
    public Response<Long> getUnreadCount(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "token", required = false) String tokenQuery) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        String userId = getUserIdFromToken(effectiveToken);
        long count = socialService.getUnreadCount(userId);
        return Response.buildSuccess(count);
    }

    @GetMapping("/relation/{targetUserId}")
    public Response<UserRelationVO> getUserRelation(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "token", required = false) String tokenQuery,
            @PathVariable String targetUserId) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        String currentUserId = getUserIdFromToken(effectiveToken);
        UserRelationVO relation = socialService.getUserRelation(currentUserId, targetUserId);
        return Response.buildSuccess(relation);
    }

    @GetMapping("/stats/{userId}")
    public Response<SocialStatsVO> getSocialStats(
            @PathVariable String userId) {
        SocialStatsVO stats = socialService.getSocialStats(userId);
        return Response.buildSuccess(stats);
    }
}
