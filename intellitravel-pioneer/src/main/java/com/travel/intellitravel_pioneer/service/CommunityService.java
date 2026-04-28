package com.travel.intellitravel_pioneer.service;

import com.travel.intellitravel_pioneer.vo.community.*;

public interface CommunityService {
    PostCreateResponse createPost(String userId, PostCreateRequest request);
    
    PaginationResponse<PostDetailVO> getPosts(String sort, String tag, int page, int size, String userId);
    
    PostDetailVO getPostDetail(String postId, String userId);
    
    InteractionResponse likePost(String postId, String userId, boolean unlike); // "action": "like" in body (maybe unlike not supported or toggle?)
                                                                                // The request just says "action": "like" or "collect". I assume toggle or explicit.
                                                                                // Wait, 4.4 and 4.5 descriptions say "Like / Cancel Like".
                                                                                // Usually implies toggle if only "action": "like" is sent.
                                                                                // Or maybe action can be "unlike". The example just shows "like".
                                                                                // I'll assume toggle for now based on "Like / Cancel Like" description.
    
    InteractionResponse collectPost(String postId, String userId, boolean uncollect);

    InteractionResponse likeComment(String commentId, String userId);

    CommentCreateResponse createComment(String postId, String userId, CommentCreateRequest request);

    ChallengeParticipateResponse participateChallenge(String userIdStr, ChallengeParticipateReqVO request);

    ChallengeParticipateResponse completeChallenge(String userIdStr, Long challengeId, ChallengeCompleteReqVO request);

    java.util.List<ChallengeHistoryVO> getUserChallengeHistory(String userId, String action);

    java.util.List<ChallengeHistoryVO> getItineraryChallengeLeaderboard(String itineraryId);

    PaginationResponse<PostDetailVO> getCollectedPosts(String userId, int page, int size);

    PaginationResponse<PostDetailVO> getMyPosts(String userId, int page, int size);

    PaginationResponse<CommunityEventVO> getMyEvents(String userId, int page, int size);

    void deletePost(String postId, String userId);

    void deleteComment(String commentId, String userId);
}
