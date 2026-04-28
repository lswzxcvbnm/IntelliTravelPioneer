package com.travel.intellitravel_pioneer.controller;

import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import com.travel.intellitravel_pioneer.po.UserPO;
import com.travel.intellitravel_pioneer.service.CommunityService;
import com.travel.intellitravel_pioneer.util.TokenUtil;
import com.travel.intellitravel_pioneer.vo.common.Response;
import com.travel.intellitravel_pioneer.vo.community.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/community")
public class CommunityController {

    @Autowired
    private CommunityService communityService;

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

    @PostMapping("/challenge/participate")
    public Response<ChallengeParticipateResponse> participateChallenge(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "token", required = false) String tokenQuery,
            @RequestBody ChallengeParticipateReqVO request) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        String userId = getUserIdFromToken(effectiveToken);
        ChallengeParticipateResponse response = communityService.participateChallenge(userId, request);
        return Response.buildSuccess(response);
    }

    @PutMapping("/challenge/{challengeId}/complete")
    public Response<ChallengeParticipateResponse> completeChallenge(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "token", required = false) String tokenQuery,
            @PathVariable Long challengeId,
            @RequestBody ChallengeCompleteReqVO request) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        String userId = getUserIdFromToken(effectiveToken);
        ChallengeParticipateResponse response = communityService.completeChallenge(userId, challengeId, request);
        return Response.buildSuccess(response);
    }
    //不传action，显示包括参与者，创建者记录，action传入participated为参与者记录，action传入created为创建者记录，传入action但值乱写就没有记录
    @GetMapping("/challenge/history")
    public Response<java.util.List<ChallengeHistoryVO>> getUserChallengeHistory(
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "token", required = false) String tokenQuery,
            @RequestParam(value = "action", required = false) String action) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        String userId = getUserIdFromToken(effectiveToken);
        java.util.List<ChallengeHistoryVO> list = communityService.getUserChallengeHistory(userId, action);
        return Response.buildSuccess(list);
    }

    @GetMapping("/challenge/leaderboard")
    public Response<java.util.List<ChallengeHistoryVO>> getItineraryChallengeLeaderboard(
            @RequestParam(value = "itinerary_id") String itineraryId) {
        java.util.List<ChallengeHistoryVO> list = communityService.getItineraryChallengeLeaderboard(itineraryId);
        return Response.buildSuccess(list);
    }

    @PostMapping("/posts")
    public Response<PostCreateResponse> createPost(@RequestHeader(value = "token", required = false) String token,
                                                   @RequestParam(value = "token", required = false) String tokenQuery,
                                                   @RequestBody PostCreateRequest request) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        String userId = getUserIdFromToken(effectiveToken);
        PostCreateResponse response = communityService.createPost(userId, request);
        return Response.buildSuccess(response);
    }

    @GetMapping("/posts")
    public Response<PaginationResponse<PostDetailVO>> getPosts(@RequestHeader(value = "token", required = false) String token,
                                                               @RequestParam(value = "token", required = false) String tokenQuery,
                                                               @RequestParam(value = "sort", defaultValue = "hot") String sort,
                                                               @RequestParam(value = "tag", required = false) String tag,
                                                               @RequestParam(value = "page", defaultValue = "1") int page,
                                                               @RequestParam(value = "size", defaultValue = "20") int size) {
        // token optional for list; accept either header or query param
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        String userId = null;
        if (effectiveToken != null && !effectiveToken.isEmpty()) {
            try {
                userId = getUserIdFromToken(effectiveToken);
            } catch (Exception e) {
                // ignore invalid
            }
        }
        PaginationResponse<PostDetailVO> response = communityService.getPosts(sort, tag, page, size, userId);
        return Response.buildSuccess(response);
    }

    @GetMapping("/posts/{postId}")
    public Response<PostDetailVO> getPostDetail(@RequestHeader(value = "token", required = false) String token,
                                                @RequestParam(value = "token", required = false) String tokenQuery,
                                                @PathVariable String postId) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        String userId = null;
        if (effectiveToken != null && !effectiveToken.isEmpty()) {
             try {
                 userId = getUserIdFromToken(effectiveToken);
             } catch (Exception e) {
                 // Treat as guest if token invalid
             }
        }
        PostDetailVO response = communityService.getPostDetail(postId, userId);
        return Response.buildSuccess(response);
    }

    @PostMapping("/posts/{postId}/like")
    public Response<InteractionResponse> likePost(@RequestHeader(value = "token", required = false) String token,
                                                  @RequestParam(value = "token", required = false) String tokenQuery,
                                                  @PathVariable String postId,
                                                  @RequestBody InteractionRequest request) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        String userId = getUserIdFromToken(effectiveToken);
        // request.action should be "like"
        boolean unlike = false; // logic handled in service (toggle)
        InteractionResponse response = communityService.likePost(postId, userId, unlike);
        return Response.buildSuccess(response);
    }

    @PostMapping("/posts/{postId}/collect")
    public Response<InteractionResponse> collectPost(@RequestHeader(value = "token", required = false) String token,
                                                    @RequestParam(value = "token", required = false) String tokenQuery,
                                                    @PathVariable String postId,
                                                    @RequestBody InteractionRequest request) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        String userId = getUserIdFromToken(effectiveToken);
        InteractionResponse response = communityService.collectPost(postId, userId, false);
        return Response.buildSuccess(response);
    }

    @PostMapping("/posts/{postId}/comments")
    public Response<CommentCreateResponse> createComment(@RequestHeader(value = "token", required = false) String token,
                                                         @RequestParam(value = "token", required = false) String tokenQuery,
                                                         @PathVariable String postId,
                                                         @RequestBody CommentCreateRequest request) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        String userId = getUserIdFromToken(effectiveToken);
        CommentCreateResponse response = communityService.createComment(postId, userId, request);
        return Response.buildSuccess(response);
    }

    @GetMapping("/posts/collected")
    public Response<PaginationResponse<PostDetailVO>> getCollectedPosts(@RequestHeader(value = "token", required = false) String token,
                                                                         @RequestParam(value = "token", required = false) String tokenQuery,
                                                                         @RequestParam(value = "page", defaultValue = "1") int page,
                                                                         @RequestParam(value = "size", defaultValue = "20") int size) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        String userId = getUserIdFromToken(effectiveToken);
        PaginationResponse<PostDetailVO> response = communityService.getCollectedPosts(userId, page, size);
        return Response.buildSuccess(response);
    }

    @PostMapping("/comments/{id}/like")
    public Response<InteractionResponse> likeComment(
            @PathVariable("id") String commentId,
            @RequestHeader(value = "token", required = false) String token,
            @RequestParam(value = "token", required = false) String tokenQuery,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        String userIdStr = getUserIdFromToken(effectiveToken);
        
        // Even if body is passed, we assume toggle behavior.
        InteractionResponse response = communityService.likeComment(commentId, userIdStr);
        return Response.buildSuccess(response);
    }

    @GetMapping("/posts/mine")
    public Response<PaginationResponse<PostDetailVO>> getMyPosts(@RequestHeader(value = "token", required = false) String token,
                                                                  @RequestParam(value = "token", required = false) String tokenQuery,
                                                                  @RequestParam(value = "page", defaultValue = "1") int page,
                                                                  @RequestParam(value = "size", defaultValue = "20") int size) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        String userId = getUserIdFromToken(effectiveToken);
        PaginationResponse<PostDetailVO> response = communityService.getMyPosts(userId, page, size);
        return Response.buildSuccess(response);
    }

    @GetMapping("/events")
    public Response<PaginationResponse<CommunityEventVO>> getMyEvents(@RequestHeader(value = "token", required = false) String token,
                                                                      @RequestParam(value = "token", required = false) String tokenQuery,
                                                                      @RequestParam(value = "page", defaultValue = "1") int page,
                                                                      @RequestParam(value = "size", defaultValue = "20") int size) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        String userId = getUserIdFromToken(effectiveToken);
        PaginationResponse<CommunityEventVO> response = communityService.getMyEvents(userId, page, size);
        return Response.buildSuccess(response);
    }

    @DeleteMapping("/posts/{postId}")
    public Response<Void> deletePost(@PathVariable String postId,
                                     @RequestHeader(value = "token", required = false) String token,
                                     @RequestParam(value = "token", required = false) String tokenQuery) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        String userId = getUserIdFromToken(effectiveToken);
        communityService.deletePost(postId, userId);
        return Response.buildSuccess((Void) null);
    }

    @DeleteMapping("/comments/{commentId}")
    public Response<Void> deleteComment(@PathVariable String commentId,
                                        @RequestHeader(value = "token", required = false) String token,
                                        @RequestParam(value = "token", required = false) String tokenQuery) {
        String effectiveToken = (token != null && !token.isEmpty()) ? token : tokenQuery;
        String userId = getUserIdFromToken(effectiveToken);
        communityService.deleteComment(commentId, userId);
        return Response.buildSuccess((Void) null);
    }
}
