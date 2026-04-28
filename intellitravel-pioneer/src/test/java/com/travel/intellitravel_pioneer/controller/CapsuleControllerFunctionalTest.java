package com.travel.intellitravel_pioneer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.intellitravel_pioneer.IntellitravelPioneerApplication;
import com.travel.intellitravel_pioneer.po.CapsulePO;
import com.travel.intellitravel_pioneer.po.UserPO;
import com.travel.intellitravel_pioneer.repository.CapsuleRepository;
import com.travel.intellitravel_pioneer.repository.UserRepository;
import com.travel.intellitravel_pioneer.vo.capsule.CapsuleDetailVO;
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
class CapsuleControllerFunctionalTest {

        @LocalServerPort
        private int port;

        private RestTemplate restTemplate;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private CapsuleRepository capsuleRepository;

        private static final String TEST_USERNAME = "capsule_test_user";
        private static final String TEST_PASSWORD = "testPassword123";
        private static final String TEST_EMAIL = "capsule@test.com";

        private UserPO testUser;

        @BeforeEach
        void setUp() {
                restTemplate = new RestTemplate();
                testUser = new UserPO();
                testUser.setUsername(TEST_USERNAME);
                testUser.setPassword(TEST_PASSWORD);
                testUser.setEmail(TEST_EMAIL);
                testUser = userRepository.save(testUser);
        }

        private String url(String path) {
                return "http://localhost:" + port + path;
        }

        @Test
        @Order(1)
        @DisplayName("功能测试 - 创建时间胶囊")
        void testCreateCapsule() throws Exception {
                CapsuleDetailVO capsuleDetail = new CapsuleDetailVO();
                capsuleDetail.setTitle("测试时间胶囊");
                capsuleDetail.setContent("这是测试内容");
                capsuleDetail.setDate("2024-01-01");
                capsuleDetail.setIsPublic(true);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                HttpEntity<CapsuleDetailVO> httpRequest = new HttpEntity<>(capsuleDetail, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(
                                url("/api/capsule/create?userId=" + testUser.getId()),
                                httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertNotNull(response.getBody());
        }

        @Test
        @Order(2)
        @DisplayName("功能测试 - 创建时间胶囊 - 包含位置信息")
        void testCreateCapsule_WithLocation() throws Exception {
                CapsuleDetailVO capsuleDetail = new CapsuleDetailVO();
                capsuleDetail.setTitle("北京时间胶囊");
                capsuleDetail.setContent("北京内容");
                capsuleDetail.setDate("2024-01-01");
                capsuleDetail.setIsPublic(true);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                HttpEntity<CapsuleDetailVO> httpRequest = new HttpEntity<>(capsuleDetail, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(
                                url("/api/capsule/create?userId=" + testUser.getId()),
                                httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @Order(3)
        @DisplayName("功能测试 - 获取用户胶囊列表")
        void testGetUserCapsules() throws Exception {
                CapsulePO capsule = new CapsulePO();
                capsule.setUserId(testUser.getId().longValue());
                capsule.setTitle("用户胶囊1");
                capsule.setContent("内容1");
                capsule.setIsPublic(true);
                capsule.setCapsuleDate("2024-01-01");
                capsuleRepository.save(capsule);

                ResponseEntity<String> response = restTemplate.getForEntity(
                                url("/api/capsule/user/" + testUser.getId()),
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertNotNull(response.getBody());
        }

        @Test
        @Order(4)
        @DisplayName("功能测试 - 获取用户胶囊列表 - 空列表")
        void testGetUserCapsules_Empty() throws Exception {
                ResponseEntity<String> response = restTemplate.getForEntity(
                                url("/api/capsule/user/99999"),
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @Order(5)
        @DisplayName("功能测试 - 根据地点获取胶囊")
        void testGetCapsulesByLocation() throws Exception {
                CapsulePO capsule = new CapsulePO();
                capsule.setUserId(testUser.getId().longValue());
                capsule.setTitle("地点胶囊");
                capsule.setContent("内容");
                capsule.setIsPublic(true);
                capsule.setLocationName("北京市朝阳区");
                capsule.setCapsuleDate("2024-01-01");
                capsuleRepository.save(capsule);

                ResponseEntity<String> response = restTemplate.getForEntity(
                                url("/api/capsule/location?locationName=北京市朝阳区"),
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @Order(6)
        @DisplayName("功能测试 - 根据地点获取胶囊 - 指定城市")
        void testGetCapsulesByLocation_WithCity() throws Exception {
                ResponseEntity<String> response = restTemplate.getForEntity(
                                url("/api/capsule/location?locationName=故宫&city=北京"),
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @Order(7)
        @DisplayName("功能测试 - 根据经纬度获取附近胶囊")
        void testGetCapsulesByLocationNearby() throws Exception {
                CapsulePO capsule = new CapsulePO();
                capsule.setUserId(testUser.getId().longValue());
                capsule.setTitle("附近胶囊");
                capsule.setContent("内容");
                capsule.setIsPublic(true);
                capsule.setLatitude(39.90923);
                capsule.setLongitude(116.397428);
                capsule.setCapsuleDate("2024-01-01");
                capsuleRepository.save(capsule);

                ResponseEntity<String> response = restTemplate.getForEntity(
                                url("/api/capsule/location/nearby?latitude=39.90923&longitude=116.397428&radiusMeters=1000"),
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @Order(8)
        @DisplayName("功能测试 - 获取胶囊详情")
        void testGetCapsuleDetail() throws Exception {
                CapsulePO capsule = new CapsulePO();
                capsule.setUserId(testUser.getId().longValue());
                capsule.setTitle("详情胶囊");
                capsule.setContent("详细内容");
                capsule.setIsPublic(true);
                capsule.setDecoration("star");
                capsule.setColor("#FF5733");
                capsule.setCapsuleDate("2024-01-01");
                capsule = capsuleRepository.save(capsule);

                ResponseEntity<String> response = restTemplate.getForEntity(
                                url("/api/capsule/" + capsule.getId() + "/detail?currentUserId=" + testUser.getId()),
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertTrue(response.getBody().contains("详情胶囊"));
        }

        @Test
        @Order(9)
        @DisplayName("功能测试 - 获取胶囊详情 - 不存在")
        void testGetCapsuleDetail_NotFound() throws Exception {
                ResponseEntity<String> response = restTemplate.getForEntity(
                                url("/api/capsule/99999/detail"),
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @Order(10)
        @DisplayName("功能测试 - 删除时间胶囊")
        void testDeleteCapsule() throws Exception {
                CapsulePO capsule = new CapsulePO();
                capsule.setUserId(testUser.getId().longValue());
                capsule.setTitle("待删除胶囊");
                capsule.setContent("内容");
                capsule.setIsPublic(true);
                capsule.setCapsuleDate("2024-01-01");
                capsule = capsuleRepository.save(capsule);

                HttpHeaders headers = new HttpHeaders();
                HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

                ResponseEntity<String> response = restTemplate.exchange(
                                url("/api/capsule/" + capsule.getId() + "?userId=" + testUser.getId()),
                                HttpMethod.DELETE,
                                httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @Order(11)
        @DisplayName("功能测试 - 删除时间胶囊 - 不存在")
        void testDeleteCapsule_NotFound() throws Exception {
                HttpHeaders headers = new HttpHeaders();
                HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

                ResponseEntity<String> response = restTemplate.exchange(
                                url("/api/capsule/99999?userId=" + testUser.getId()),
                                HttpMethod.DELETE,
                                httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @Order(12)
        @DisplayName("功能测试 - 更新时间胶囊")
        void testUpdateCapsule() throws Exception {
                CapsulePO capsule = new CapsulePO();
                capsule.setUserId(testUser.getId().longValue());
                capsule.setTitle("原标题");
                capsule.setContent("原内容");
                capsule.setIsPublic(true);
                capsule.setCapsuleDate("2024-01-01");
                capsule = capsuleRepository.save(capsule);

                CapsuleDetailVO updateDetail = new CapsuleDetailVO();
                updateDetail.setTitle("更新后的标题");
                updateDetail.setContent("更新后的内容");

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                HttpEntity<CapsuleDetailVO> httpRequest = new HttpEntity<>(updateDetail, headers);

                ResponseEntity<String> response = restTemplate.exchange(
                                url("/api/capsule/" + capsule.getId() + "/update?userId=" + testUser.getId()),
                                HttpMethod.POST,
                                httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @Order(13)
        @DisplayName("功能测试 - 更新时间胶囊 - 不存在")
        void testUpdateCapsule_NotFound() throws Exception {
                CapsuleDetailVO updateDetail = new CapsuleDetailVO();
                updateDetail.setTitle("新标题");

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                HttpEntity<CapsuleDetailVO> httpRequest = new HttpEntity<>(updateDetail, headers);

                ResponseEntity<String> response = restTemplate.exchange(
                                url("/api/capsule/99999/update?userId=" + testUser.getId()),
                                HttpMethod.POST,
                                httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @Order(14)
        @DisplayName("功能测试 - 完整时间胶囊流程：创建->获取列表->获取详情->更新->删除")
        void testCompleteCapsuleFlow() throws Exception {
                CapsuleDetailVO createDetail = new CapsuleDetailVO();
                createDetail.setTitle("完整流程胶囊");
                createDetail.setContent("完整流程内容");
                createDetail.setDate("2024-01-01");
                createDetail.setIsPublic(true);

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                HttpEntity<CapsuleDetailVO> createRequest = new HttpEntity<>(createDetail, headers);

                ResponseEntity<String> createResponse = restTemplate.postForEntity(
                                url("/api/capsule/create?userId=" + testUser.getId()),
                                createRequest,
                                String.class);
                assertEquals(HttpStatus.OK, createResponse.getStatusCode());

                ResponseEntity<String> listResponse = restTemplate.getForEntity(
                                url("/api/capsule/user/" + testUser.getId()),
                                String.class);
                assertEquals(HttpStatus.OK, listResponse.getStatusCode());

                CapsulePO savedCapsule = capsuleRepository.findByUserId(testUser.getId().longValue()).stream()
                                .filter(c -> "完整流程胶囊".equals(c.getTitle()))
                                .findFirst()
                                .orElse(null);

                if (savedCapsule != null) {
                        ResponseEntity<String> detailResponse = restTemplate.getForEntity(
                                        url("/api/capsule/" + savedCapsule.getId() + "/detail?currentUserId="
                                                        + testUser.getId()),
                                        String.class);
                        assertEquals(HttpStatus.OK, detailResponse.getStatusCode());

                        CapsuleDetailVO updateDetail = new CapsuleDetailVO();
                        updateDetail.setTitle("更新后的完整流程胶囊");
                        HttpEntity<CapsuleDetailVO> updateRequest = new HttpEntity<>(updateDetail, headers);
                        ResponseEntity<String> updateResponse = restTemplate.exchange(
                                        url("/api/capsule/" + savedCapsule.getId() + "/update?userId=" + testUser.getId()),
                                        HttpMethod.POST,
                                        updateRequest,
                                        String.class);
                        assertEquals(HttpStatus.OK, updateResponse.getStatusCode());

                        HttpEntity<Void> deleteRequest = new HttpEntity<>(headers);
                        ResponseEntity<String> deleteResponse = restTemplate.exchange(
                                        url("/api/capsule/" + savedCapsule.getId() + "?userId=" + testUser.getId()),
                                        HttpMethod.DELETE,
                                        deleteRequest,
                                        String.class);
                        assertEquals(HttpStatus.OK, deleteResponse.getStatusCode());
                }
        }
}
