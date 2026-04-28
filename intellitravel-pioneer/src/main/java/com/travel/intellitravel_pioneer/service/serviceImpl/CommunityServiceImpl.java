package com.travel.intellitravel_pioneer.service.serviceImpl;

import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import com.travel.intellitravel_pioneer.po.CommunityEventPO;
import com.travel.intellitravel_pioneer.po.CollectPO;
import com.travel.intellitravel_pioneer.po.CommentPO;
import com.travel.intellitravel_pioneer.po.LikePO;
import com.travel.intellitravel_pioneer.po.RelayChallengePO;
import com.travel.intellitravel_pioneer.po.PostPO;
import com.travel.intellitravel_pioneer.po.UserPO;
import com.travel.intellitravel_pioneer.repository.*;
import com.travel.intellitravel_pioneer.service.CommunityService;
import com.travel.intellitravel_pioneer.vo.community.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CommunityServiceImpl implements CommunityService {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private CollectRepository collectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RelayChallengeRepository relayChallengeRepository;

    @Autowired
    private CommunityEventRepository communityEventRepository;

    // Use DateTimeFormatter directly inside methods or static final field
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    @Transactional
    public PostCreateResponse createPost(String userIdStr, PostCreateRequest request) {
        Integer userId = Integer.parseInt(userIdStr);
        if (!userRepository.existsById(userId)) {
            throw new IntellitravelException("User not found");
        }

        PostPO post = new PostPO();
        post.setSummaryId(request.getSummaryId());
        post.setTitle(request.getTitle() != null ? request.getTitle() : "My Trip Summary"); // Default title if null
        post.setContent(request.getContent());
        post.setImages(request.getImages() != null ? request.getImages() : new ArrayList<>());
        post.setUserId(userId);
        post.setIsPublic(request.getIsPublic() != null ? request.getIsPublic() : true);
        post.setTags(request.getTags());
        
        // Connect itinerary id
        post.setItineraryId(request.getItineraryId());
        
        // Save post
        post = postRepository.save(post);

        String postUrl = "https://your-domain.com/community/" + post.getId(); // Construct URL dynamically if possible

        return PostCreateResponse.builder()
                .postId(post.getId())
                .postUrl(postUrl)
                .createdAt(post.getCreatedAt().format(ISO_FORMATTER))
                .build();
    }

    @Override
    public PaginationResponse<PostDetailVO> getPosts(String sort, String tag, int page, int size, String userIdStr) {
        if (page < 1) page = 1;
        // Sorting logic
        Sort sortObj = Sort.by(Sort.Direction.DESC, "createdAt");
        if ("hot".equalsIgnoreCase(sort)) {
            sortObj = Sort.by(Sort.Direction.DESC, "likeCount", "commentCount", "createdAt");
        } else if ("new".equalsIgnoreCase(sort)) {
            sortObj = Sort.by(Sort.Direction.DESC, "createdAt");
        }

        Pageable pageable = PageRequest.of(page - 1, size, sortObj);
        
        Page<PostPO> postPage;
        if (tag != null && !tag.isEmpty()) {
            postPage = postRepository.findByTagsContaining(tag, pageable);
        } else {
            postPage = postRepository.findAll(pageable);
        }

        Integer userId = null;
        if (userIdStr != null) {
            try {
                userId = Integer.parseInt(userIdStr);
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        
        // Final userId for lambda
        final Integer finalUserId = userId;

        List<PostDetailVO> list = postPage.getContent().stream()
                .map(post -> convertToDetailVO(post, finalUserId)) // We might want a simplified converter for list view, but detail VO works
                .collect(Collectors.toList());

        return new PaginationResponse<>(postPage.getTotalElements(), list);
    }

    @Override
    public PostDetailVO getPostDetail(String postId, String userIdStr) {
        PostPO post = postRepository.findById(postId)
                .orElseThrow(() -> new IntellitravelException("Post not found"));
        
        Integer userId = null;
        if (userIdStr != null) {
            try {
                userId = Integer.parseInt(userIdStr);
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        
        PostDetailVO vo = convertToDetailVO(post, userId);
        
        // Fetch comments
        List<CommentPO> comments = commentRepository.findByPostIdOrderByCreatedAtDesc(postId);
        // final modifier for lambda
        final Integer finalUserId = userId;
        
        List<CommentVO> commentVOs = comments.stream()
                .map(c -> convertToCommentVO(c, finalUserId))
                .collect(Collectors.toList());
        
        // build parent-child hierarchy
        java.util.Map<String, CommentVO> commentMap = new java.util.HashMap<>();
        for (CommentVO c : commentVOs) {
            commentMap.put(c.getCommentId(), c);
            c.setReplies(new java.util.ArrayList<>()); // initialize replies list
        }
        java.util.List<CommentVO> nestedComments = new java.util.ArrayList<>();
        for (CommentVO c : commentVOs) {
            if (c.getParentCommentId() == null || c.getParentCommentId().isEmpty()) {
                nestedComments.add(c);
            } else {
                CommentVO parent = commentMap.get(c.getParentCommentId());
                if (parent != null) {
                    parent.getReplies().add(c);
                } else {
                    // missing parent, treat as root
                    nestedComments.add(c);
                }
            }
        }
        vo.setComments(nestedComments);

        // Fetch summary content (mock or real)
        if (post.getSummaryId() != null) {
            // Ideally fetch from Summary table or Itinerary
            // Using placeholder logic:
            SummaryVO summaryVO = SummaryVO.builder()
                    .summaryId(post.getSummaryId())
                    .content("Summary content for " + post.getSummaryId()) // Placeholder
                    .build();
            vo.setSummary(summaryVO);
        }

        return vo;
    }

    @Override
    @Transactional
    public InteractionResponse likePost(String postId, String userIdStr, boolean unlike) { // Using 'unlike' request param? No, check if exists and toggle
        Integer userId = Integer.parseInt(userIdStr);
        PostPO post = postRepository.findById(postId)
                .orElseThrow(() -> new IntellitravelException("Post not found"));

        Optional<LikePO> existingLike = likeRepository.findByUserIdAndTargetIdAndType(userId, postId, LikePO.LikeType.POST);
        
        boolean isLiked;
        if (existingLike.isPresent()) {
            // Unlike
            likeRepository.delete(existingLike.get());
            post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
            isLiked = false;
        } else {
            // Like
            LikePO like = new LikePO();
            like.setUserId(userId);
            like.setTargetId(postId);
            like.setType(LikePO.LikeType.POST);
            likeRepository.save(like);
            post.setLikeCount(post.getLikeCount() + 1);
            isLiked = true;

            if (post.getUserId() != null && !post.getUserId().equals(userId)) {
                createCommunityEvent(
                        userId,
                        post.getUserId(),
                        CommunityEventPO.EventType.POST_LIKED,
                        post.getId(),
                        null,
                    buildEventDetail(userId, CommunityEventPO.EventType.POST_LIKED, post.getTitle())
                );
            }
        }
        postRepository.save(post);

        // Check if collected just for response completeness (though not strictly required perfectly by specific endpoint logic usually)
        boolean isCollected = collectRepository.existsByUserIdAndPostId(userId, postId);

        return InteractionResponse.builder()
                .currentLikeCount(post.getLikeCount())
                .isLiked(isLiked)
                // Optional: return collect status too if front-end needs it
            .currentCollectCount(post.getCollectCount() == null ? 0 : post.getCollectCount())
            .collectCount(post.getCollectCount() == null ? 0 : post.getCollectCount())
                .isCollected(isCollected)
                .build();
    }

    @Override
    @Transactional
    public InteractionResponse collectPost(String postId, String userIdStr, boolean uncollect) {
        Integer userId = Integer.parseInt(userIdStr);
        PostPO post = postRepository.findById(postId)
                .orElseThrow(() -> new IntellitravelException("Post not found"));

        Optional<CollectPO> existingCollect = collectRepository.findByUserIdAndPostId(userId, postId);
        
        boolean isCollected;
        if (existingCollect.isPresent()) {
            collectRepository.delete(existingCollect.get());
            post.setCollectCount(Math.max(0, post.getCollectCount() - 1));
            isCollected = false;
        } else {
            CollectPO collect = new CollectPO();
            collect.setUserId(userId);
            collect.setPostId(postId);
            collectRepository.save(collect);
            post.setCollectCount(post.getCollectCount() + 1);
            isCollected = true;

            if (post.getUserId() != null && !post.getUserId().equals(userId)) {
                createCommunityEvent(
                        userId,
                        post.getUserId(),
                        CommunityEventPO.EventType.POST_COLLECTED,
                        post.getId(),
                        null,
                    buildEventDetail(userId, CommunityEventPO.EventType.POST_COLLECTED, post.getTitle())
                );
            }
        }
        postRepository.save(post);

        boolean isLiked = likeRepository.existsByUserIdAndTargetIdAndType(userId, postId, LikePO.LikeType.POST);

        return InteractionResponse.builder()
            .currentCollectCount(post.getCollectCount() == null ? 0 : post.getCollectCount())
            .collectCount(post.getCollectCount() == null ? 0 : post.getCollectCount())
                .isCollected(isCollected)
                .currentLikeCount(post.getLikeCount())
                .isLiked(isLiked)
                .build();
    }

    @Override
    @Transactional
    public CommentCreateResponse createComment(String postId, String userIdStr, CommentCreateRequest request) {
        Integer userId = Integer.parseInt(userIdStr);
        if (!userRepository.existsById(userId)) {
             throw new IntellitravelException("User not found");
        }
        
        PostPO post = postRepository.findById(postId)
                .orElseThrow(() -> new IntellitravelException("Post not found"));

        CommentPO comment = new CommentPO();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setContent(request.getContent());
        comment.setParentCommentId(request.getParentCommentId());
        
        comment = commentRepository.save(comment);
        
        post.setCommentCount(post.getCommentCount() + 1);
        postRepository.save(post);

        if (post.getUserId() != null && !post.getUserId().equals(userId)) {
            createCommunityEvent(
                userId,
                post.getUserId(),
                CommunityEventPO.EventType.POST_COMMENTED,
                post.getId(),
                null,
                buildEventDetail(userId, CommunityEventPO.EventType.POST_COMMENTED, post.getTitle())
            );
        }

        return CommentCreateResponse.builder()
                .commentId(comment.getId())
                .createdAt(comment.getCreatedAt().format(ISO_FORMATTER))
                .currentCommentCount(post.getCommentCount())
                .build();
    }

    @Override
    @Transactional
    public ChallengeParticipateResponse participateChallenge(String userIdStr, ChallengeParticipateReqVO request) {
        Integer userId = Integer.parseInt(userIdStr);
        String action = request.getAction() == null ? "participate" : request.getAction().trim().toLowerCase(Locale.ROOT);
        String postId = request.getPostId();

        if (postId == null || postId.isBlank()) {
            throw new IntellitravelException("Post ID is required");
        }

        if ("cancel".equals(action)) {
            RelayChallengePO challenge = relayChallengeRepository.findByUserIdAndPostId(userId, postId)
                    .orElseThrow(() -> new IntellitravelException("Challenge participation record not found"));

            if (challenge.getStatus() != null && challenge.getStatus() != 0) {
                throw new IntellitravelException("Only pending challenge participation can be canceled");
            }

            relayChallengeRepository.delete(challenge);

            return ChallengeParticipateResponse.builder()
                    .challengeId(challenge.getId())
                    .action("cancel")
                    .status(null)
                    .build();
        }

        if (!"participate".equals(action)) {
            throw new IntellitravelException("Unsupported challenge action");
        }

        Optional<PostPO> postOpt = postRepository.findById(postId);
        if (postOpt.isEmpty()) {
            throw new IntellitravelException("Post not found");
        }
        PostPO post = postOpt.get();
        if (post.getItineraryId() == null || post.getItineraryId().isEmpty()) {
            throw new IntellitravelException("Post does not have an itinerary attached");
        }

        if (relayChallengeRepository.existsByUserIdAndPostId(userId, postId)) {
            throw new IntellitravelException("User already participated in this challenge");
        }

        RelayChallengePO challenge = RelayChallengePO.builder()
                .userId(userId)
                .postId(postId)
                .itineraryId(request.getItineraryId() != null ? request.getItineraryId() : post.getItineraryId())
                .status(0) // 0: enrolled/pending
                .build();
                
        relayChallengeRepository.save(challenge);

        if (post.getUserId() != null && !post.getUserId().equals(userId)) {
            createCommunityEvent(
                userId,
                post.getUserId(),
                CommunityEventPO.EventType.CHALLENGE_PARTICIPATED,
                post.getId(),
                challenge.getId(),
                buildEventDetail(userId, CommunityEventPO.EventType.CHALLENGE_PARTICIPATED, post.getTitle())
            );
        }
        
        return ChallengeParticipateResponse.builder()
            .challengeId(challenge.getId())
            .action("participate")
            .status(challenge.getStatus())
            .build();
    }

    @Override
    @Transactional
    public ChallengeParticipateResponse completeChallenge(String userIdStr, Long challengeId, ChallengeCompleteReqVO request) {
        Integer userId = Integer.parseInt(userIdStr);
        
        Optional<RelayChallengePO> challengeOpt = relayChallengeRepository.findById(challengeId);
        if (challengeOpt.isEmpty()) {
            throw new IntellitravelException("Challenge record not found");
        }
        
        RelayChallengePO challenge = challengeOpt.get();
        
        if (!challenge.getUserId().equals(userId)) {
            throw new IntellitravelException("You are not authorized to complete this challenge");
        }
        
        if (challenge.getStatus() != 0) {
            throw new IntellitravelException("Challenge is already completed or reviewed");
        }
        
        if ((request.getProofImages() == null || request.getProofImages().isEmpty()) && 
            (request.getFitnessData() == null || request.getFitnessData().isEmpty())) {
            throw new IntellitravelException("Proof images or fitness data are required to complete the challenge");
        }

        challenge.setProofImages(request.getProofImages());
        challenge.setFitnessData(request.getFitnessData());
        if (request.getDurationMinutes() != null) {
            challenge.setDurationMinutes(request.getDurationMinutes());
        }
        challenge.setStatus(1); // 1: completed/submitted for review
        
        relayChallengeRepository.save(challenge);

        PostPO post = postRepository.findById(challenge.getPostId()).orElse(null);
        if (post != null && post.getUserId() != null && !post.getUserId().equals(userId)) {
            createCommunityEvent(
                    userId,
                    post.getUserId(),
                    CommunityEventPO.EventType.CHALLENGE_COMPLETED,
                    post.getId(),
                    challenge.getId(),
                    buildEventDetail(userId, CommunityEventPO.EventType.CHALLENGE_COMPLETED, post.getTitle())
            );
        }
        
        return ChallengeParticipateResponse.builder()
            .challengeId(challenge.getId())
            .action("complete")
            .status(challenge.getStatus())
            .build();
    }

    @Override
    public List<ChallengeHistoryVO> getUserChallengeHistory(String userIdStr, String action) {
        Integer userId = Integer.parseInt(userIdStr);
        List<RelayChallengePO> records = relayChallengeRepository.findByUserId(userId);
        List<RelayChallengePO> createdRecords = relayChallengeRepository.findByCreatorId(userId);
        
        // when action is null we simply concatenate both lists (no deduplication)
        if (action == null) {
            List<ChallengeHistoryVO> all = new ArrayList<>();
            for (RelayChallengePO record : records) {
                String postTitle = "Unknown Post";
                Optional<PostPO> postOpt = postRepository.findById(record.getPostId());
                if (postOpt.isPresent()) {
                    postTitle = postOpt.get().getTitle();
                }
                String username = getUsernameByUserId(record.getUserId());
                all.add(ChallengeHistoryVO.builder()
                        .id(record.getId())
                        .userId(record.getUserId())
                        .username(username)
                        .postId(record.getPostId())
                        .itineraryId(record.getItineraryId())
                        .postTitle(postTitle)
                        .proofImages(record.getProofImages())
                        .fitnessData(record.getFitnessData())
                        .durationMinutes(record.getDurationMinutes())
                        .status(record.getStatus())
                        .role("participant")
                        .createdAt(record.getCreatedAt() != null ? record.getCreatedAt().format(ISO_FORMATTER) : "")
                        .build());
            }
            for (RelayChallengePO record : createdRecords) {
                String postTitle = "Unknown Post";
                Optional<PostPO> postOpt = postRepository.findById(record.getPostId());
                if (postOpt.isPresent()) {
                    postTitle = postOpt.get().getTitle();
                }
                String username = getUsernameByUserId(record.getUserId());
                all.add(ChallengeHistoryVO.builder()
                        .id(record.getId())
                        .userId(record.getUserId())
                        .username(username)
                        .postId(record.getPostId())
                        .itineraryId(record.getItineraryId())
                        .postTitle(postTitle)
                        .proofImages(record.getProofImages())
                        .fitnessData(record.getFitnessData())
                        .durationMinutes(record.getDurationMinutes())
                        .status(record.getStatus())
                        .role("creator")
                        .createdAt(record.getCreatedAt() != null ? record.getCreatedAt().format(ISO_FORMATTER) : "")
                        .build());
            }
            return all;
        }
        
        // otherwise follow filtered and dedup path
        java.util.Map<Long, ChallengeHistoryVO> map = new java.util.LinkedHashMap<>();

        if (action.equals("participated")) {
            for (RelayChallengePO record : records) {
                String postTitle = "Unknown Post";
                Optional<PostPO> postOpt = postRepository.findById(record.getPostId());
                if (postOpt.isPresent()) {
                    postTitle = postOpt.get().getTitle();
                }
                String username = getUsernameByUserId(record.getUserId());
                
                ChallengeHistoryVO vo = ChallengeHistoryVO.builder()
                        .id(record.getId())
                        .userId(record.getUserId())
                        .username(username)
                        .postId(record.getPostId())
                        .itineraryId(record.getItineraryId())
                        .postTitle(postTitle)
                        .proofImages(record.getProofImages())
                        .fitnessData(record.getFitnessData())
                        .durationMinutes(record.getDurationMinutes())
                        .status(record.getStatus())
                        .role("participant")
                        .createdAt(record.getCreatedAt() != null ? record.getCreatedAt().format(ISO_FORMATTER) : "")
                        .build();
                map.put(record.getId(), vo);
            }
        }

        if (action.equals("created")) {
            for (RelayChallengePO record : createdRecords) {
                if (map.containsKey(record.getId())) {
                    ChallengeHistoryVO existing = map.get(record.getId());
                    existing.setRole("creator");
                    continue;
                }
                String postTitle = "Unknown Post";
                Optional<PostPO> postOpt = postRepository.findById(record.getPostId());
                if (postOpt.isPresent()) {
                    postTitle = postOpt.get().getTitle();
                }
                String username = getUsernameByUserId(record.getUserId());
                
                ChallengeHistoryVO vo = ChallengeHistoryVO.builder()
                        .id(record.getId())
                        .userId(record.getUserId())
                    .username(username)
                        .postId(record.getPostId())
                        .itineraryId(record.getItineraryId())
                        .postTitle(postTitle)
                        .proofImages(record.getProofImages())
                        .fitnessData(record.getFitnessData())                        .durationMinutes(record.getDurationMinutes())                        .status(record.getStatus())
                        .role("creator")
                        .createdAt(record.getCreatedAt() != null ? record.getCreatedAt().format(ISO_FORMATTER) : "")
                        .build();
                map.put(record.getId(), vo);
            }
        }
        
        return new ArrayList<>(map.values());
    }

    @Override
    public List<ChallengeHistoryVO> getItineraryChallengeLeaderboard(String itineraryId) {
        // Fetch approved challenges (status = 1 is typically approved) ordered by duration asc
        List<RelayChallengePO> records = relayChallengeRepository
                .findByItineraryIdAndStatusOrderByDurationMinutesAsc(itineraryId, 1);
        
        return records.stream().map(record -> {
            String username = getUsernameByUserId(record.getUserId());
            String postTitle = "Unknown Post";
            if (record.getPostId() != null) {
                postTitle = postRepository.findById(record.getPostId())
                        .map(PostPO::getTitle)
                        .orElse("Unknown Post");
            }
            return ChallengeHistoryVO.builder()
                    .id(record.getId())
                    .userId(record.getUserId())
                    .username(username)
                    .postId(record.getPostId())
                    .itineraryId(record.getItineraryId())
                    .postTitle(postTitle)
                    .proofImages(record.getProofImages())
                    .fitnessData(record.getFitnessData())
                    .durationMinutes(record.getDurationMinutes())
                    .status(record.getStatus())
                    .role("creator") // Default role, you could logic here to detect if creator
                    .createdAt(record.getCreatedAt() != null ? record.getCreatedAt().format(ISO_FORMATTER) : "")
                    .build();
        }).collect(Collectors.toList());
    }

    private String getUsernameByUserId(Integer userId) {
        return userRepository.findById(userId)
                .map(UserPO::getUsername)
                .orElse("Unknown User");
    }

    @Override
    public PaginationResponse<PostDetailVO> getCollectedPosts(String userIdStr, int page, int size) {
        Integer userId = Integer.parseInt(userIdStr);
        if (page < 1) page = 1;
        org.springframework.data.domain.Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        org.springframework.data.domain.Page<CollectPO> collects = collectRepository.findByUserId(userId, pageable);

        java.util.List<PostDetailVO> list = new java.util.ArrayList<>();
        for (CollectPO c : collects.getContent()) {
            PostPO post = postRepository.findById(c.getPostId()).orElse(null);
            if (post != null) {
                list.add(convertToDetailVO(post, userId));
            }
        }

        return new PaginationResponse<>(collects.getTotalElements(), list);
    }

    @Override
    public PaginationResponse<PostDetailVO> getMyPosts(String userIdStr, int page, int size) {
        Integer userId = Integer.parseInt(userIdStr);
        if (page < 1) page = 1;
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<PostPO> postPage = postRepository.findByUserId(userId, pageable);
        List<PostDetailVO> list = postPage.getContent().stream()
                .map(p -> convertToDetailVO(p, userId))
                .collect(Collectors.toList());
        return new PaginationResponse<>(postPage.getTotalElements(), list);
    }

    @Override
    public PaginationResponse<CommunityEventVO> getMyEvents(String userIdStr, int page, int size) {
        Integer userId = Integer.parseInt(userIdStr);
        if (page < 1) page = 1;

        Pageable pageable = PageRequest.of(page - 1, size);
        Page<CommunityEventPO> eventPage = communityEventRepository.findByReceiverUserIdOrderByCreatedAtDesc(userId, pageable);

        List<CommunityEventVO> list = eventPage.getContent().stream()
                .map(this::convertToCommunityEventVO)
                .collect(Collectors.toList());

        return new PaginationResponse<>(eventPage.getTotalElements(), list);
    }

    @Override
    @Transactional
    public void deletePost(String postId, String userIdStr) {
        Integer userId = Integer.parseInt(userIdStr);
        PostPO post = postRepository.findById(postId)
                .orElseThrow(() -> new IntellitravelException("Post not found"));
        if (!post.getUserId().equals(userId)) {
            throw new IntellitravelException("Not authorized to delete this post");
        }
        // remove associated comments, likes, collects
        commentRepository.deleteAllByPostId(postId);
        likeRepository.deleteAllByTargetIdAndType(postId, LikePO.LikeType.POST);
        collectRepository.deleteAllByPostId(postId);
        postRepository.delete(post);
    }

    @Override
    @Transactional
    public void deleteComment(String commentId, String userIdStr) {
        Integer userId = Integer.parseInt(userIdStr);
        CommentPO comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IntellitravelException("Comment not found"));
        if (!comment.getUserId().equals(userId)) {
            throw new IntellitravelException("Not authorized to delete this comment");
        }
        // decrement post comment count
        PostPO post = postRepository.findById(comment.getPostId()).orElse(null);
        if (post != null) {
            post.setCommentCount(Math.max(0, post.getCommentCount() - 1));
            postRepository.save(post);
        }
        likeRepository.deleteAllByTargetIdAndType(commentId, LikePO.LikeType.COMMENT);
        commentRepository.delete(comment);
    }

    private PostDetailVO convertToDetailVO(PostPO post, Integer currentUserId) {
        if (post == null) {
            return null;
        }
        UserPO author = userRepository.findById(post.getUserId()).orElse(null);
        String avatar = author != null ? author.getAvatar() : "";
        String nickname = author != null ? author.getName() : "Unknown User"; // AuthorVO uses name field, or username? Let's check AuthorVO.

        boolean isLiked = false;
        boolean isCollected = false;

        if (currentUserId != null) {
            // Fix method calls based on previously observed correct usage in read_file
            isLiked = likeRepository.existsByUserIdAndTargetIdAndType(currentUserId, post.getId(), LikePO.LikeType.POST);
            isCollected = collectRepository.existsByUserIdAndPostId(currentUserId, post.getId());
        }

        return PostDetailVO.builder()
                .postId(post.getId())
                .title(post.getTitle())
                .previewContent(post.getContent() != null && (post.getContent().length() > 100) ?
                        post.getContent().substring(0, 100) + "..." : post.getContent())
                .content(post.getContent())
                .previewImages(post.getImages() != null && !post.getImages().isEmpty() ?
                        post.getImages().subList(0, Math.min(post.getImages().size(), 3)) : new ArrayList<>())
                .images(post.getImages())
                .author(AuthorVO.builder()
                        .userId(post.getUserId())
                        .username(author != null ? author.getUsername() : "")
                        .name(nickname)
                        .avatar(avatar)
                        .build())
                // .stats(...) // PostStatsVO seems missing, maybe fields are directly on PostDetailVO or different structure
                // .interaction(...) // UserInteractionVO seems missing
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .collectCount(post.getCollectCount() == null ? 0 : post.getCollectCount())
                .isLiked(isLiked)
                .isCollected(isCollected)
                .tags(post.getTags() != null ? Arrays.asList(post.getTags().split(",")) : new ArrayList<>())
                .createdAt(post.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .itineraryId(post.getItineraryId())
                .build();
    }

    private CommentVO convertToCommentVO(CommentPO comment, Integer currentUserId) {
        UserPO author = userRepository.findById(comment.getUserId()).orElse(null);
        String name = "Unknown";
        if (author != null) {
             name = author.getName();
             if (name == null || name.isEmpty()) {
                 name = author.getUsername();
             }
        }
        String avatar = "";
        if (author != null && author.getAvatar() != null && !author.getAvatar().isEmpty()) {
            avatar = author.getAvatar();
        }

        boolean isLiked = false;
        if (currentUserId != null) {
            isLiked = likeRepository.existsByUserIdAndTargetIdAndType(currentUserId, comment.getId(), LikePO.LikeType.COMMENT);
        }

        return CommentVO.builder()
                .commentId(comment.getId())
                .content(comment.getContent())
                .authorName(name)
                .authorAvatar(avatar)
                .createdAt(comment.getCreatedAt().toString())
                .likeCount(comment.getLikeCount())
                .parentCommentId(comment.getParentCommentId())
                .isLiked(isLiked)
                .replies(new java.util.ArrayList<>())
                .build();
    }

    @Override
    @Transactional
    public InteractionResponse likeComment(String commentId, String userIdStr) {
        Integer userId = Integer.parseInt(userIdStr);
        CommentPO comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IntellitravelException("Comment not found"));

        Optional<LikePO> existingLike = likeRepository.findByUserIdAndTargetIdAndType(userId, commentId, LikePO.LikeType.COMMENT);
        
        boolean isLiked;
        if (existingLike.isPresent()) {
            // Unlike
            likeRepository.delete(existingLike.get());
            comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
            isLiked = false;
        } else {
            // Like
            LikePO like = new LikePO();
            like.setUserId(userId);
            like.setTargetId(commentId);
            like.setType(LikePO.LikeType.COMMENT);
            likeRepository.save(like);
            comment.setLikeCount(comment.getLikeCount() + 1);
            isLiked = true;
        }
        commentRepository.save(comment);

        return InteractionResponse.builder()
                .currentLikeCount(comment.getLikeCount())
                .isLiked(isLiked)
                .build();
    }

    private CommunityEventVO convertToCommunityEventVO(CommunityEventPO event) {
        String actorUsername = getUsernameByUserId(event.getActorUserId());
        String receiverUsername = getUsernameByUserId(event.getReceiverUserId());

        return CommunityEventVO.builder()
                .eventId(event.getId())
                .eventType(event.getEventType().name())
                .actorUserId(event.getActorUserId())
                .actorUsername(actorUsername)
                .receiverUserId(event.getReceiverUserId())
                .receiverUsername(receiverUsername)
                .postId(event.getPostId())
                .challengeId(event.getChallengeId())
                .eventDetail(event.getEventDetail())
                .createdAt(event.getCreatedAt() != null ? event.getCreatedAt().format(ISO_FORMATTER) : "")
                .build();
    }

    private void createCommunityEvent(Integer actorUserId,
                                      Integer receiverUserId,
                                      CommunityEventPO.EventType eventType,
                                      String postId,
                                      Long challengeId,
                                      String eventDetail) {
        CommunityEventPO event = new CommunityEventPO();
        event.setActorUserId(actorUserId);
        event.setReceiverUserId(receiverUserId);
        event.setEventType(eventType);
        event.setPostId(postId);
        event.setChallengeId(challengeId);
        event.setEventDetail(eventDetail);
        communityEventRepository.save(event);
    }

    private String buildEventDetail(Integer actorUserId, CommunityEventPO.EventType eventType, String postTitle) {
        String actorUsername = getUsernameByUserId(actorUserId);
        String safePostTitle = (postTitle == null || postTitle.isBlank()) ? "未命名帖子" : postTitle;

        switch (eventType) {
            case POST_LIKED:
                return actorUsername + "点赞了我的帖子《" + safePostTitle + "》";
            case POST_COLLECTED:
                return actorUsername + "收藏了我的帖子《" + safePostTitle + "》";
            case CHALLENGE_PARTICIPATED:
                return actorUsername + "接力挑战了我的《" + safePostTitle + "》里的路线";
            case CHALLENGE_COMPLETED:
                return actorUsername + "完成了我的《" + safePostTitle + "》里的挑战";
            case POST_COMMENTED:
                // User requested format: 谁谁谁评论了我的帖子<帖子名>
                return actorUsername + "评论了我的帖子《" + safePostTitle + "》";
            default:
                return actorUsername + "触发了新的社区事件";
        }
    }
}
