package com.travel.intellitravel_pioneer.service.serviceImpl;

import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import com.travel.intellitravel_pioneer.po.*;
import com.travel.intellitravel_pioneer.repository.*;
import com.travel.intellitravel_pioneer.vo.community.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.*;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommunityServiceImplTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private CollectRepository collectRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RelayChallengeRepository relayChallengeRepository;

    @Mock
    private CommunityEventRepository communityEventRepository;

    @InjectMocks
    private CommunityServiceImpl communityService;

    private PostPO testPost;
    private UserPO testUser;
    private CommentPO testComment;

    @BeforeEach
    void setUp() {
        testUser = new UserPO();
        testUser.setId(1);
        testUser.setUsername("testuser");
        testUser.setName("Test User");
        testUser.setAvatar("https://example.com/avatar.jpg");

        testPost = new PostPO();
        testPost.setId("post-123");
        testPost.setUserId(1);
        testPost.setTitle("Test Post");
        testPost.setContent("Test Content");
        testPost.setImages(new ArrayList<>());
        testPost.setLikeCount(0);
        testPost.setCommentCount(0);
        testPost.setCollectCount(0);
        testPost.setIsPublic(true);
        testPost.setCreatedAt(LocalDateTime.now());
        testPost.setItineraryId("itinerary-123");

        testComment = new CommentPO();
        testComment.setId("comment-123");
        testComment.setPostId("post-123");
        testComment.setUserId(1);
        testComment.setContent("Test Comment");
        testComment.setLikeCount(0);
        testComment.setCreatedAt(LocalDateTime.now());

        lenient().when(userRepository.existsById(anyInt())).thenReturn(true);
        lenient().when(userRepository.findById(anyInt())).thenReturn(Optional.of(testUser));
    }

    @Test
    @DisplayName("创建帖子 - 成功创建")
    void testCreatePost_Success() {
        PostCreateRequest request = new PostCreateRequest();
        request.setTitle("New Post");
        request.setContent("New Content");
        request.setIsPublic(true);

        when(postRepository.save(any(PostPO.class))).thenReturn(testPost);

        PostCreateResponse response = communityService.createPost("1", request);

        assertNotNull(response);
        assertEquals("post-123", response.getPostId());
        verify(postRepository, times(1)).save(any(PostPO.class));
    }

    @Test
    @DisplayName("创建帖子 - 用户不存在抛出异常")
    void testCreatePost_UserNotFound() {
        when(userRepository.existsById(anyInt())).thenReturn(false);

        PostCreateRequest request = new PostCreateRequest();
        request.setTitle("New Post");

        IntellitravelException exception = assertThrows(IntellitravelException.class,
                () -> communityService.createPost("1", request));

        assertEquals("User not found", exception.getMessage());
    }

    @Test
    @DisplayName("创建帖子 - 包含图片")
    void testCreatePost_WithImages() {
        PostCreateRequest request = new PostCreateRequest();
        request.setTitle("New Post");
        request.setImages(Arrays.asList("image1.jpg", "image2.jpg"));

        when(postRepository.save(any(PostPO.class))).thenReturn(testPost);

        PostCreateResponse response = communityService.createPost("1", request);

        assertNotNull(response);
        verify(postRepository, times(1)).save(any(PostPO.class));
    }

    @Test
    @DisplayName("获取帖子列表 - 成功获取")
    void testGetPosts_Success() {
        Page<PostPO> postPage = new PageImpl<>(Arrays.asList(testPost));
        when(postRepository.findAll(any(Pageable.class))).thenReturn(postPage);

        PaginationResponse<PostDetailVO> response = communityService.getPosts("new", null, 1, 10, "1");

        assertNotNull(response);
        assertEquals(1, response.getList().size());
    }

    @Test
    @DisplayName("获取帖子列表 - 按热度排序")
    void testGetPosts_SortByHot() {
        Page<PostPO> postPage = new PageImpl<>(Arrays.asList(testPost));
        when(postRepository.findAll(any(Pageable.class))).thenReturn(postPage);

        PaginationResponse<PostDetailVO> response = communityService.getPosts("hot", null, 1, 10, "1");

        assertNotNull(response);
        assertEquals(1, response.getList().size());
    }

    @Test
    @DisplayName("获取帖子列表 - 按标签过滤")
    void testGetPosts_FilterByTag() {
        testPost.setTags("travel,food");
        Page<PostPO> postPage = new PageImpl<>(Arrays.asList(testPost));
        when(postRepository.findByTagsContaining(anyString(), any(Pageable.class))).thenReturn(postPage);

        PaginationResponse<PostDetailVO> response = communityService.getPosts("new", "travel", 1, 10, "1");

        assertNotNull(response);
        assertEquals(1, response.getList().size());
    }

    @Test
    @DisplayName("获取帖子详情 - 成功获取")
    void testGetPostDetail_Success() {
        when(postRepository.findById("post-123")).thenReturn(Optional.of(testPost));
        when(commentRepository.findByPostIdOrderByCreatedAtDesc(anyString())).thenReturn(new ArrayList<>());

        PostDetailVO response = communityService.getPostDetail("post-123", "1");

        assertNotNull(response);
        assertEquals("Test Post", response.getTitle());
    }

    @Test
    @DisplayName("获取帖子详情 - 帖子不存在抛出异常")
    void testGetPostDetail_PostNotFound() {
        when(postRepository.findById("post-123")).thenReturn(Optional.empty());

        IntellitravelException exception = assertThrows(IntellitravelException.class,
                () -> communityService.getPostDetail("post-123", "1"));

        assertEquals("Post not found", exception.getMessage());
    }

    @Test
    @DisplayName("获取帖子详情 - 包含评论")
    void testGetPostDetail_WithComments() {
        when(postRepository.findById("post-123")).thenReturn(Optional.of(testPost));
        when(commentRepository.findByPostIdOrderByCreatedAtDesc(anyString())).thenReturn(Arrays.asList(testComment));

        PostDetailVO response = communityService.getPostDetail("post-123", "1");

        assertNotNull(response);
        assertEquals(1, response.getComments().size());
    }

    @Test
    @DisplayName("点赞帖子 - 成功点赞")
    void testLikePost_Success() {
        when(postRepository.findById("post-123")).thenReturn(Optional.of(testPost));
        when(likeRepository.findByUserIdAndTargetIdAndType(anyInt(), anyString(), eq(LikePO.LikeType.POST)))
                .thenReturn(Optional.empty());
        when(likeRepository.save(any(LikePO.class))).thenReturn(new LikePO());
        when(postRepository.save(any(PostPO.class))).thenReturn(testPost);
        when(collectRepository.existsByUserIdAndPostId(anyInt(), anyString())).thenReturn(false);

        InteractionResponse response = communityService.likePost("post-123", "1", false);

        assertNotNull(response);
        assertTrue(response.getIsLiked());
        assertEquals(1, response.getCurrentLikeCount());
    }

    @Test
    @DisplayName("点赞帖子 - 取消点赞")
    void testLikePost_Unlike() {
        LikePO existingLike = new LikePO();
        existingLike.setId(1L);
        existingLike.setUserId(1);
        existingLike.setTargetId("post-123");
        existingLike.setType(LikePO.LikeType.POST);

        testPost.setLikeCount(1);

        when(postRepository.findById("post-123")).thenReturn(Optional.of(testPost));
        when(likeRepository.findByUserIdAndTargetIdAndType(anyInt(), anyString(), eq(LikePO.LikeType.POST)))
                .thenReturn(Optional.of(existingLike));
        doNothing().when(likeRepository).delete(any(LikePO.class));
        when(postRepository.save(any(PostPO.class))).thenReturn(testPost);
        when(collectRepository.existsByUserIdAndPostId(anyInt(), anyString())).thenReturn(false);

        InteractionResponse response = communityService.likePost("post-123", "1", false);

        assertNotNull(response);
        assertFalse(response.getIsLiked());
        assertEquals(0, response.getCurrentLikeCount());
    }

    @Test
    @DisplayName("点赞帖子 - 帖子不存在抛出异常")
    void testLikePost_PostNotFound() {
        when(postRepository.findById("post-123")).thenReturn(Optional.empty());

        IntellitravelException exception = assertThrows(IntellitravelException.class,
                () -> communityService.likePost("post-123", "1", false));

        assertEquals("Post not found", exception.getMessage());
    }

    @Test
    @DisplayName("收藏帖子 - 成功收藏")
    void testCollectPost_Success() {
        when(postRepository.findById("post-123")).thenReturn(Optional.of(testPost));
        when(collectRepository.findByUserIdAndPostId(anyInt(), anyString())).thenReturn(Optional.empty());
        when(collectRepository.save(any(CollectPO.class))).thenReturn(new CollectPO());
        when(postRepository.save(any(PostPO.class))).thenReturn(testPost);
        when(likeRepository.existsByUserIdAndTargetIdAndType(anyInt(), anyString(), eq(LikePO.LikeType.POST))).thenReturn(false);

        InteractionResponse response = communityService.collectPost("post-123", "1", false);

        assertNotNull(response);
        assertTrue(response.getIsCollected());
        assertEquals(1, response.getCurrentCollectCount());
    }

    @Test
    @DisplayName("收藏帖子 - 取消收藏")
    void testCollectPost_Uncollect() {
        CollectPO existingCollect = new CollectPO();
        existingCollect.setId(1L);
        existingCollect.setUserId(1);
        existingCollect.setPostId("post-123");

        testPost.setCollectCount(1);

        when(postRepository.findById("post-123")).thenReturn(Optional.of(testPost));
        when(collectRepository.findByUserIdAndPostId(anyInt(), anyString())).thenReturn(Optional.of(existingCollect));
        doNothing().when(collectRepository).delete(any(CollectPO.class));
        when(postRepository.save(any(PostPO.class))).thenReturn(testPost);
        when(likeRepository.existsByUserIdAndTargetIdAndType(anyInt(), anyString(), eq(LikePO.LikeType.POST))).thenReturn(false);

        InteractionResponse response = communityService.collectPost("post-123", "1", false);

        assertNotNull(response);
        assertFalse(response.getIsCollected());
        assertEquals(0, response.getCurrentCollectCount());
    }

    @Test
    @DisplayName("创建评论 - 成功创建")
    void testCreateComment_Success() {
        CommentCreateRequest request = new CommentCreateRequest();
        request.setContent("New Comment");

        when(postRepository.findById("post-123")).thenReturn(Optional.of(testPost));
        when(commentRepository.save(any(CommentPO.class))).thenReturn(testComment);
        when(postRepository.save(any(PostPO.class))).thenReturn(testPost);

        CommentCreateResponse response = communityService.createComment("post-123", "1", request);

        assertNotNull(response);
        assertEquals("comment-123", response.getCommentId());
    }

    @Test
    @DisplayName("创建评论 - 帖子不存在抛出异常")
    void testCreateComment_PostNotFound() {
        CommentCreateRequest request = new CommentCreateRequest();
        request.setContent("New Comment");

        when(postRepository.findById("post-123")).thenReturn(Optional.empty());

        IntellitravelException exception = assertThrows(IntellitravelException.class,
                () -> communityService.createComment("post-123", "1", request));

        assertEquals("Post not found", exception.getMessage());
    }

    @Test
    @DisplayName("创建评论 - 回复评论")
    void testCreateComment_ReplyToComment() {
        CommentCreateRequest request = new CommentCreateRequest();
        request.setContent("Reply Comment");
        request.setParentCommentId("parent-comment-123");

        when(postRepository.findById("post-123")).thenReturn(Optional.of(testPost));
        when(commentRepository.save(any(CommentPO.class))).thenReturn(testComment);
        when(postRepository.save(any(PostPO.class))).thenReturn(testPost);

        CommentCreateResponse response = communityService.createComment("post-123", "1", request);

        assertNotNull(response);
        verify(commentRepository, times(1)).save(any(CommentPO.class));
    }

    @Test
    @DisplayName("参与挑战 - 成功参与")
    void testParticipateChallenge_Success() {
        ChallengeParticipateReqVO request = new ChallengeParticipateReqVO();
        request.setAction("participate");
        request.setPostId("post-123");

        when(postRepository.findById("post-123")).thenReturn(Optional.of(testPost));
        when(relayChallengeRepository.existsByUserIdAndPostId(anyInt(), anyString())).thenReturn(false);
        when(relayChallengeRepository.save(any(RelayChallengePO.class))).thenReturn(RelayChallengePO.builder().id(1L).build());

        ChallengeParticipateResponse response = communityService.participateChallenge("1", request);

        assertNotNull(response);
        assertEquals("participate", response.getAction());
    }

    @Test
    @DisplayName("参与挑战 - 帖子不存在抛出异常")
    void testParticipateChallenge_PostNotFound() {
        ChallengeParticipateReqVO request = new ChallengeParticipateReqVO();
        request.setAction("participate");
        request.setPostId("post-123");

        when(postRepository.findById("post-123")).thenReturn(Optional.empty());

        IntellitravelException exception = assertThrows(IntellitravelException.class,
                () -> communityService.participateChallenge("1", request));

        assertEquals("Post not found", exception.getMessage());
    }

    @Test
    @DisplayName("参与挑战 - 帖子无行程抛出异常")
    void testParticipateChallenge_NoItinerary() {
        testPost.setItineraryId(null);
        ChallengeParticipateReqVO request = new ChallengeParticipateReqVO();
        request.setAction("participate");
        request.setPostId("post-123");

        when(postRepository.findById("post-123")).thenReturn(Optional.of(testPost));

        IntellitravelException exception = assertThrows(IntellitravelException.class,
                () -> communityService.participateChallenge("1", request));

        assertEquals("Post does not have an itinerary attached", exception.getMessage());
    }

    @Test
    @DisplayName("参与挑战 - 已参与抛出异常")
    void testParticipateChallenge_AlreadyParticipated() {
        ChallengeParticipateReqVO request = new ChallengeParticipateReqVO();
        request.setAction("participate");
        request.setPostId("post-123");

        when(postRepository.findById("post-123")).thenReturn(Optional.of(testPost));
        when(relayChallengeRepository.existsByUserIdAndPostId(anyInt(), anyString())).thenReturn(true);

        IntellitravelException exception = assertThrows(IntellitravelException.class,
                () -> communityService.participateChallenge("1", request));

        assertEquals("User already participated in this challenge", exception.getMessage());
    }

    @Test
    @DisplayName("取消挑战 - 成功取消")
    void testParticipateChallenge_Cancel() {
        RelayChallengePO challenge = RelayChallengePO.builder()
                .id(1L)
                .userId(1)
                .postId("post-123")
                .status(0)
                .build();

        ChallengeParticipateReqVO request = new ChallengeParticipateReqVO();
        request.setAction("cancel");
        request.setPostId("post-123");

        when(relayChallengeRepository.findByUserIdAndPostId(anyInt(), anyString())).thenReturn(Optional.of(challenge));
        doNothing().when(relayChallengeRepository).delete(any(RelayChallengePO.class));

        ChallengeParticipateResponse response = communityService.participateChallenge("1", request);

        assertNotNull(response);
        assertEquals("cancel", response.getAction());
    }

    @Test
    @DisplayName("完成挑战 - 成功完成")
    void testCompleteChallenge_Success() {
        RelayChallengePO challenge = RelayChallengePO.builder()
                .id(1L)
                .userId(1)
                .postId("post-123")
                .status(0)
                .build();

        ChallengeCompleteReqVO request = new ChallengeCompleteReqVO();
        request.setProofImages(Arrays.asList("proof1.jpg"));

        when(relayChallengeRepository.findById(1L)).thenReturn(Optional.of(challenge));
        when(relayChallengeRepository.save(any(RelayChallengePO.class))).thenReturn(challenge);
        when(postRepository.findById("post-123")).thenReturn(Optional.of(testPost));

        ChallengeParticipateResponse response = communityService.completeChallenge("1", 1L, request);

        assertNotNull(response);
        assertEquals("complete", response.getAction());
        assertEquals(1, response.getStatus());
    }

    @Test
    @DisplayName("完成挑战 - 挑战不存在抛出异常")
    void testCompleteChallenge_ChallengeNotFound() {
        ChallengeCompleteReqVO request = new ChallengeCompleteReqVO();
        request.setProofImages(Arrays.asList("proof1.jpg"));

        when(relayChallengeRepository.findById(1L)).thenReturn(Optional.empty());

        IntellitravelException exception = assertThrows(IntellitravelException.class,
                () -> communityService.completeChallenge("1", 1L, request));

        assertEquals("Challenge record not found", exception.getMessage());
    }

    @Test
    @DisplayName("完成挑战 - 无权限抛出异常")
    void testCompleteChallenge_NotAuthorized() {
        RelayChallengePO challenge = RelayChallengePO.builder()
                .id(1L)
                .userId(2)
                .postId("post-123")
                .status(0)
                .build();

        ChallengeCompleteReqVO request = new ChallengeCompleteReqVO();
        request.setProofImages(Arrays.asList("proof1.jpg"));

        when(relayChallengeRepository.findById(1L)).thenReturn(Optional.of(challenge));

        IntellitravelException exception = assertThrows(IntellitravelException.class,
                () -> communityService.completeChallenge("1", 1L, request));

        assertEquals("You are not authorized to complete this challenge", exception.getMessage());
    }

    @Test
    @DisplayName("完成挑战 - 无证明材料抛出异常")
    void testCompleteChallenge_NoProof() {
        RelayChallengePO challenge = RelayChallengePO.builder()
                .id(1L)
                .userId(1)
                .postId("post-123")
                .status(0)
                .build();

        ChallengeCompleteReqVO request = new ChallengeCompleteReqVO();

        when(relayChallengeRepository.findById(1L)).thenReturn(Optional.of(challenge));

        IntellitravelException exception = assertThrows(IntellitravelException.class,
                () -> communityService.completeChallenge("1", 1L, request));

        assertEquals("Proof images or fitness data are required to complete the challenge", exception.getMessage());
    }

    @Test
    @DisplayName("获取用户挑战历史 - 成功获取")
    void testGetUserChallengeHistory_Success() {
        RelayChallengePO challenge = RelayChallengePO.builder()
                .id(1L)
                .userId(1)
                .postId("post-123")
                .status(0)
                .createdAt(LocalDateTime.now())
                .build();

        when(relayChallengeRepository.findByUserId(anyInt())).thenReturn(Arrays.asList(challenge));
        when(relayChallengeRepository.findByCreatorId(anyInt())).thenReturn(new ArrayList<>());
        when(postRepository.findById("post-123")).thenReturn(Optional.of(testPost));

        List<ChallengeHistoryVO> response = communityService.getUserChallengeHistory("1", null);

        assertNotNull(response);
        assertEquals(1, response.size());
    }

    @Test
    @DisplayName("获取用户挑战历史 - 按参与过滤")
    void testGetUserChallengeHistory_FilterByParticipated() {
        RelayChallengePO challenge = RelayChallengePO.builder()
                .id(1L)
                .userId(1)
                .postId("post-123")
                .status(0)
                .createdAt(LocalDateTime.now())
                .build();

        when(relayChallengeRepository.findByUserId(anyInt())).thenReturn(Arrays.asList(challenge));
        when(postRepository.findById("post-123")).thenReturn(Optional.of(testPost));

        List<ChallengeHistoryVO> response = communityService.getUserChallengeHistory("1", "participated");

        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals("participant", response.get(0).getRole());
    }

    @Test
    @DisplayName("获取收藏帖子 - 成功获取")
    void testGetCollectedPosts_Success() {
        CollectPO collect = new CollectPO();
        collect.setPostId("post-123");
        collect.setCreatedAt(LocalDateTime.now());

        Page<CollectPO> collectPage = new PageImpl<>(Arrays.asList(collect));

        when(collectRepository.findByUserId(anyInt(), any(Pageable.class))).thenReturn(collectPage);
        when(postRepository.findById("post-123")).thenReturn(Optional.of(testPost));

        PaginationResponse<PostDetailVO> response = communityService.getCollectedPosts("1", 1, 10);

        assertNotNull(response);
        assertEquals(1, response.getList().size());
    }

    @Test
    @DisplayName("获取我的帖子 - 成功获取")
    void testGetMyPosts_Success() {
        Page<PostPO> postPage = new PageImpl<>(Arrays.asList(testPost));

        when(postRepository.findByUserId(anyInt(), any(Pageable.class))).thenReturn(postPage);

        PaginationResponse<PostDetailVO> response = communityService.getMyPosts("1", 1, 10);

        assertNotNull(response);
        assertEquals(1, response.getList().size());
    }

    @Test
    @DisplayName("获取我的事件 - 成功获取")
    void testGetMyEvents_Success() {
        CommunityEventPO event = new CommunityEventPO();
        event.setId(1L);
        event.setActorUserId(2);
        event.setReceiverUserId(1);
        event.setEventType(CommunityEventPO.EventType.POST_LIKED);
        event.setEventDetail("User liked your post");
        event.setCreatedAt(LocalDateTime.now());

        Page<CommunityEventPO> eventPage = new PageImpl<>(Arrays.asList(event));

        when(communityEventRepository.findByReceiverUserIdOrderByCreatedAtDesc(anyInt(), any(Pageable.class))).thenReturn(eventPage);

        PaginationResponse<CommunityEventVO> response = communityService.getMyEvents("1", 1, 10);

        assertNotNull(response);
        assertEquals(1, response.getList().size());
    }

    @Test
    @DisplayName("删除帖子 - 成功删除")
    void testDeletePost_Success() {
        when(postRepository.findById("post-123")).thenReturn(Optional.of(testPost));
        doNothing().when(commentRepository).deleteAllByPostId(anyString());
        doNothing().when(likeRepository).deleteAllByTargetIdAndType(anyString(), any(LikePO.LikeType.class));
        doNothing().when(collectRepository).deleteAllByPostId(anyString());
        doNothing().when(postRepository).delete(any(PostPO.class));

        assertDoesNotThrow(() -> communityService.deletePost("post-123", "1"));

        verify(postRepository, times(1)).delete(testPost);
    }

    @Test
    @DisplayName("删除帖子 - 帖子不存在抛出异常")
    void testDeletePost_PostNotFound() {
        when(postRepository.findById("post-123")).thenReturn(Optional.empty());

        IntellitravelException exception = assertThrows(IntellitravelException.class,
                () -> communityService.deletePost("post-123", "1"));

        assertEquals("Post not found", exception.getMessage());
    }

    @Test
    @DisplayName("删除帖子 - 无权限抛出异常")
    void testDeletePost_NotAuthorized() {
        when(postRepository.findById("post-123")).thenReturn(Optional.of(testPost));

        IntellitravelException exception = assertThrows(IntellitravelException.class,
                () -> communityService.deletePost("post-123", "2"));

        assertEquals("Not authorized to delete this post", exception.getMessage());
    }

    @Test
    @DisplayName("删除评论 - 成功删除")
    void testDeleteComment_Success() {
        when(commentRepository.findById("comment-123")).thenReturn(Optional.of(testComment));
        when(postRepository.findById("post-123")).thenReturn(Optional.of(testPost));
        doNothing().when(likeRepository).deleteAllByTargetIdAndType(anyString(), any(LikePO.LikeType.class));
        doNothing().when(commentRepository).delete(any(CommentPO.class));
        when(postRepository.save(any(PostPO.class))).thenReturn(testPost);

        assertDoesNotThrow(() -> communityService.deleteComment("comment-123", "1"));

        verify(commentRepository, times(1)).delete(testComment);
    }

    @Test
    @DisplayName("删除评论 - 评论不存在抛出异常")
    void testDeleteComment_CommentNotFound() {
        when(commentRepository.findById("comment-123")).thenReturn(Optional.empty());

        IntellitravelException exception = assertThrows(IntellitravelException.class,
                () -> communityService.deleteComment("comment-123", "1"));

        assertEquals("Comment not found", exception.getMessage());
    }

    @Test
    @DisplayName("删除评论 - 无权限抛出异常")
    void testDeleteComment_NotAuthorized() {
        when(commentRepository.findById("comment-123")).thenReturn(Optional.of(testComment));

        IntellitravelException exception = assertThrows(IntellitravelException.class,
                () -> communityService.deleteComment("comment-123", "2"));

        assertEquals("Not authorized to delete this comment", exception.getMessage());
    }

    @Test
    @DisplayName("点赞评论 - 成功点赞")
    void testLikeComment_Success() {
        when(commentRepository.findById("comment-123")).thenReturn(Optional.of(testComment));
        when(likeRepository.findByUserIdAndTargetIdAndType(anyInt(), anyString(), eq(LikePO.LikeType.COMMENT)))
                .thenReturn(Optional.empty());
        when(likeRepository.save(any(LikePO.class))).thenReturn(new LikePO());
        when(commentRepository.save(any(CommentPO.class))).thenReturn(testComment);

        InteractionResponse response = communityService.likeComment("comment-123", "1");

        assertNotNull(response);
        assertTrue(response.getIsLiked());
    }

    @Test
    @DisplayName("点赞评论 - 取消点赞")
    void testLikeComment_Unlike() {
        LikePO existingLike = new LikePO();
        existingLike.setId(1L);
        existingLike.setUserId(1);
        existingLike.setTargetId("comment-123");
        existingLike.setType(LikePO.LikeType.COMMENT);

        testComment.setLikeCount(1);

        when(commentRepository.findById("comment-123")).thenReturn(Optional.of(testComment));
        when(likeRepository.findByUserIdAndTargetIdAndType(anyInt(), anyString(), eq(LikePO.LikeType.COMMENT)))
                .thenReturn(Optional.of(existingLike));
        doNothing().when(likeRepository).delete(any(LikePO.class));
        when(commentRepository.save(any(CommentPO.class))).thenReturn(testComment);

        InteractionResponse response = communityService.likeComment("comment-123", "1");

        assertNotNull(response);
        assertFalse(response.getIsLiked());
    }

    @Test
    @DisplayName("点赞评论 - 评论不存在抛出异常")
    void testLikeComment_CommentNotFound() {
        when(commentRepository.findById("comment-123")).thenReturn(Optional.empty());

        IntellitravelException exception = assertThrows(IntellitravelException.class,
                () -> communityService.likeComment("comment-123", "1"));

        assertEquals("Comment not found", exception.getMessage());
    }
}
