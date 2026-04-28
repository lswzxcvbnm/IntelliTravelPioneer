package com.travel.intellitravel_pioneer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.intellitravel_pioneer.IntellitravelPioneerApplication;
import com.travel.intellitravel_pioneer.po.*;
import com.travel.intellitravel_pioneer.repository.*;
import com.travel.intellitravel_pioneer.util.TokenUtil;
import com.travel.intellitravel_pioneer.vo.community.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = IntellitravelPioneerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional
class CommunityControllerFunctionalTest {

        @LocalServerPort
        private int port;

        private RestTemplate restTemplate;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private PostRepository postRepository;

        @Autowired
        private CommentRepository commentRepository;

        @Autowired
        private ItineraryRepository itineraryRepository;

        @Autowired
        private TokenUtil tokenUtil;

        private static final String TEST_USERNAME = "community_test_user";
        private static final String TEST_PASSWORD = "testPassword123";
        private static final String TEST_EMAIL = "community@test.com";

        private UserPO testUser;
        private String testToken;
        private PostPO testPost;

        @BeforeEach
        void setUp() {
                restTemplate = new RestTemplate();
                testUser = new UserPO();
                testUser.setUsername(TEST_USERNAME);
                testUser.setPassword(TEST_PASSWORD);
                testUser.setEmail(TEST_EMAIL);
                testUser = userRepository.save(testUser);

                testToken = tokenUtil.getToken(testUser);

                testPost = new PostPO();
                testPost.setUserId(testUser.getId());
                testPost.setTitle("测试帖子");
                testPost.setContent("测试内容");
                testPost.setIsPublic(true);
                testPost.setLikeCount(0);
                testPost.setCommentCount(0);
                testPost.setCollectCount(0);
                testPost = postRepository.save(testPost);
        }

        private String url(String path) {
                return "http://localhost:" + port + path;
        }

        @Test
        @Order(1)
        @DisplayName("功能测试 - 创建帖子")
        void testCreatePost() throws Exception {
                PostCreateRequest request = new PostCreateRequest();
                request.setTitle("新帖子");
                request.setContent("新帖子内容");
                request.setIsPublic(true);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                headers.set("token", testToken);
                HttpEntity<PostCreateRequest> httpRequest = new HttpEntity<>(request, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(
                                url("/api/community/posts"),
                                httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertNotNull(response.getBody());
        }

        @Test
        @Order(2)
        @DisplayName("功能测试 - 创建帖子 - 无效Token")
        void testCreatePost_InvalidToken() throws Exception {
                PostCreateRequest request = new PostCreateRequest();
                request.setTitle("新帖子");

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                headers.set("token", "invalid_token");
                HttpEntity<PostCreateRequest> httpRequest = new HttpEntity<>(request, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(
                                url("/api/community/posts"),
                                httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertTrue(response.getBody().contains("500"));
        }

        @Test
        @Order(3)
        @DisplayName("功能测试 - 获取帖子列表")
        void testGetPosts() throws Exception {
                HttpHeaders headers = new HttpHeaders();
                headers.set("token", testToken);
                HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

                ResponseEntity<String> response = restTemplate.exchange(
                                url("/api/community/posts?sort=new&page=1&size=10"),
                                HttpMethod.GET,
                                httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertNotNull(response.getBody());
        }

        @Test
        @Order(4)
        @DisplayName("功能测试 - 获取帖子详情")
        void testGetPostDetail() throws Exception {
                HttpHeaders headers = new HttpHeaders();
                headers.set("token", testToken);
                HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

                ResponseEntity<String> response = restTemplate.exchange(
                                url("/api/community/posts/" + testPost.getId()),
                                HttpMethod.GET,
                                httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertTrue(response.getBody().contains("测试帖子"));
        }

        @Test
        @Order(5)
        @DisplayName("功能测试 - 点赞帖子")
        void testLikePost() throws Exception {
                InteractionRequest request = new InteractionRequest();
                request.setAction("like");

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                headers.set("token", testToken);
                HttpEntity<InteractionRequest> httpRequest = new HttpEntity<>(request, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(
                                url("/api/community/posts/" + testPost.getId() + "/like"),
                                httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @Order(6)
        @DisplayName("功能测试 - 收藏帖子")
        void testCollectPost() throws Exception {
                InteractionRequest request = new InteractionRequest();
                request.setAction("collect");

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                headers.set("token", testToken);
                HttpEntity<InteractionRequest> httpRequest = new HttpEntity<>(request, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(
                                url("/api/community/posts/" + testPost.getId() + "/collect"),
                                httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @Order(7)
        @DisplayName("功能测试 - 创建评论")
        void testCreateComment() throws Exception {
                CommentCreateRequest request = new CommentCreateRequest();
                request.setContent("测试评论");

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                headers.set("token", testToken);
                HttpEntity<CommentCreateRequest> httpRequest = new HttpEntity<>(request, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(
                                url("/api/community/posts/" + testPost.getId() + "/comments"),
                                httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @Order(8)
        @DisplayName("功能测试 - 获取收藏帖子")
        void testGetCollectedPosts() throws Exception {
                HttpHeaders headers = new HttpHeaders();
                headers.set("token", testToken);
                HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

                ResponseEntity<String> response = restTemplate.exchange(
                                url("/api/community/posts/collected?page=1&size=10"),
                                HttpMethod.GET,
                                httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @Order(9)
        @DisplayName("功能测试 - 获取我的帖子")
        void testGetMyPosts() throws Exception {
                HttpHeaders headers = new HttpHeaders();
                headers.set("token", testToken);
                HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

                ResponseEntity<String> response = restTemplate.exchange(
                                url("/api/community/posts/mine?page=1&size=10"),
                                HttpMethod.GET,
                                httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @Order(10)
        @DisplayName("功能测试 - 获取我的事件")
        void testGetMyEvents() throws Exception {
                HttpHeaders headers = new HttpHeaders();
                headers.set("token", testToken);
                HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

                ResponseEntity<String> response = restTemplate.exchange(
                                url("/api/community/events?page=1&size=10"),
                                HttpMethod.GET,
                                httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @Order(11)
        @DisplayName("功能测试 - 点赞评论")
        void testLikeComment() throws Exception {
                CommentPO comment = new CommentPO();
                comment.setPostId(testPost.getId());
                comment.setUserId(testUser.getId());
                comment.setContent("测试评论");
                comment.setLikeCount(0);
                comment = commentRepository.save(comment);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                headers.set("token", testToken);
                HttpEntity<Void> httpRequest = new HttpEntity<>(null, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(
                                url("/api/community/comments/" + comment.getId() + "/like"),
                                httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @Order(12)
        @DisplayName("功能测试 - 删除帖子")
        void testDeletePost() throws Exception {
                PostPO post = new PostPO();
                post.setUserId(testUser.getId());
                post.setTitle("待删除帖子");
                post.setContent("内容");
                post.setIsPublic(true);
                post = postRepository.save(post);

                HttpHeaders headers = new HttpHeaders();
                headers.set("token", testToken);
                HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

                ResponseEntity<String> response = restTemplate.exchange(
                                url("/api/community/posts/" + post.getId()),
                                HttpMethod.DELETE,
                                httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @Order(13)
        @DisplayName("功能测试 - 删除评论")
        void testDeleteComment() throws Exception {
                CommentPO comment = new CommentPO();
                comment.setPostId(testPost.getId());
                comment.setUserId(testUser.getId());
                comment.setContent("待删除评论");
                comment.setLikeCount(0);
                comment = commentRepository.save(comment);

                HttpHeaders headers = new HttpHeaders();
                headers.set("token", testToken);
                HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

                ResponseEntity<String> response = restTemplate.exchange(
                                url("/api/community/comments/" + comment.getId()),
                                HttpMethod.DELETE,
                                httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @Order(14)
        @DisplayName("功能测试 - 参与挑战")
        void testParticipateChallenge() throws Exception {
                ChallengeParticipateReqVO request = new ChallengeParticipateReqVO();
                request.setAction("participate");
                request.setPostId(testPost.getId());

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                headers.set("token", testToken);
                HttpEntity<ChallengeParticipateReqVO> httpRequest = new HttpEntity<>(request, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(
                                url("/api/community/challenge/participate"),
                                httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @Order(15)
        @DisplayName("功能测试 - 获取挑战历史")
        void testGetUserChallengeHistory() throws Exception {
                HttpHeaders headers = new HttpHeaders();
                headers.set("token", testToken);
                HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

                ResponseEntity<String> response = restTemplate.exchange(
                                url("/api/community/challenge/history"),
                                HttpMethod.GET,
                                httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @Order(16)
        @DisplayName("功能测试 - 完整社区互动流程：创建帖子->点赞->评论->获取详情->删除")
        void testCompleteCommunityFlow() throws Exception {
                PostCreateRequest createRequest = new PostCreateRequest();
                createRequest.setTitle("完整流程帖子");
                createRequest.setContent("完整流程内容");
                createRequest.setIsPublic(true);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                headers.set("token", testToken);

                HttpEntity<PostCreateRequest> createHttpRequest = new HttpEntity<>(createRequest, headers);
                ResponseEntity<String> createResponse = restTemplate.postForEntity(
                                url("/api/community/posts"),
                                createHttpRequest,
                                String.class);
                assertEquals(HttpStatus.OK, createResponse.getStatusCode());

                HttpEntity<Void> listHttpRequest = new HttpEntity<>(headers);
                ResponseEntity<String> listResponse = restTemplate.exchange(
                                url("/api/community/posts?sort=new"),
                                HttpMethod.GET,
                                listHttpRequest,
                                String.class);
                assertEquals(HttpStatus.OK, listResponse.getStatusCode());

                InteractionRequest likeRequest = new InteractionRequest();
                likeRequest.setAction("like");
                HttpEntity<InteractionRequest> likeHttpRequest = new HttpEntity<>(likeRequest, headers);
                ResponseEntity<String> likeResponse = restTemplate.postForEntity(
                                url("/api/community/posts/" + testPost.getId() + "/like"),
                                likeHttpRequest,
                                String.class);
                assertEquals(HttpStatus.OK, likeResponse.getStatusCode());

                CommentCreateRequest commentRequest = new CommentCreateRequest();
                commentRequest.setContent("完整流程评论");
                HttpEntity<CommentCreateRequest> commentHttpRequest = new HttpEntity<>(commentRequest, headers);
                ResponseEntity<String> commentResponse = restTemplate.postForEntity(
                                url("/api/community/posts/" + testPost.getId() + "/comments"),
                                commentHttpRequest,
                                String.class);
                assertEquals(HttpStatus.OK, commentResponse.getStatusCode());

                HttpEntity<Void> detailHttpRequest = new HttpEntity<>(headers);
                ResponseEntity<String> detailResponse = restTemplate.exchange(
                                url("/api/community/posts/" + testPost.getId()),
                                HttpMethod.GET,
                                detailHttpRequest,
                                String.class);
                assertEquals(HttpStatus.OK, detailResponse.getStatusCode());
        }
}
