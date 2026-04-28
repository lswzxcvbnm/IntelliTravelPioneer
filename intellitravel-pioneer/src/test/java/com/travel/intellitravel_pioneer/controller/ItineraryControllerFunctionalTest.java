package com.travel.intellitravel_pioneer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.intellitravel_pioneer.IntellitravelPioneerApplication;
import com.travel.intellitravel_pioneer.po.ItineraryPO;
import com.travel.intellitravel_pioneer.po.UserPO;
import com.travel.intellitravel_pioneer.repository.ItineraryRepository;
import com.travel.intellitravel_pioneer.repository.UserRepository;
import com.travel.intellitravel_pioneer.util.TokenUtil;
import com.travel.intellitravel_pioneer.vo.itinerary.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = IntellitravelPioneerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional
class ItineraryControllerFunctionalTest {

        @LocalServerPort
        private int port;

        private RestTemplate restTemplate;

        @Autowired
        private ObjectMapper objectMapper;

        @Autowired
        private UserRepository userRepository;

        @Autowired
        private ItineraryRepository itineraryRepository;

        @Autowired
        private TokenUtil tokenUtil;

        private static final String TEST_USERNAME = "itinerary_test_user";
        private static final String TEST_PASSWORD = "testPassword123";
        private static final String TEST_EMAIL = "itinerary@test.com";

        private UserPO testUser;
        private String testToken;

        @BeforeEach
        void setUp() {
                restTemplate = new RestTemplate();
                testUser = new UserPO();
                testUser.setUsername(TEST_USERNAME);
                testUser.setPassword(TEST_PASSWORD);
                testUser.setEmail(TEST_EMAIL);
                testUser = userRepository.save(testUser);

                testToken = tokenUtil.getToken(testUser);
        }

        private String url(String path) {
                return "http://localhost:" + port + path;
        }

        @Test
        @Order(1)
        @DisplayName("功能测试 - 生成行程规划")
        void testGenerateItinerary() throws Exception {
                ItineraryRequest request = ItineraryRequest.builder()
                                .destination("北京")
                                .days(3)
                                .interests(Arrays.asList("历史", "美食"))
                                .budgetLevel("中等")
                                .additionalNotes("希望体验当地文化")
                                .build();

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                headers.set("token", testToken);
                HttpEntity<ItineraryRequest> httpRequest = new HttpEntity<>(request, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(url("/api/itinerary/generate"),
                                httpRequest, String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertNotNull(response.getBody());
                assertTrue(response.getBody().contains("destination") || response.getBody().contains("dailyPlans"));
        }

        @Test
        @Order(2)
        @DisplayName("功能测试 - 生成行程规划 - 缺少目的地")
        void testGenerateItinerary_MissingDestination() throws Exception {
                ItineraryRequest request = ItineraryRequest.builder()
                                .days(3)
                                .interests(Arrays.asList("历史"))
                                .build();

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                headers.set("token", testToken);
                HttpEntity<ItineraryRequest> httpRequest = new HttpEntity<>(request, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(url("/api/itinerary/generate"),
                                httpRequest, String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @Order(3)
        @DisplayName("功能测试 - 保存行程规划")
        void testSaveItinerary() throws Exception {
                DayPlan dayPlan = DayPlan.builder()
                                .dayNumber(1)
                                .items(Collections.emptyList())
                                .build();

                ItinerarySaveRequest request = ItinerarySaveRequest.builder()
                                .title("北京三日游")
                                .estimatedCost(3000.0)
                                .totalDays(3)
                                .destination("北京")
                                .dailyPlans(Arrays.asList(dayPlan))
                                .build();

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                headers.set("token", testToken);
                HttpEntity<ItinerarySaveRequest> httpRequest = new HttpEntity<>(request, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(url("/api/itinerary/save"), httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertNotNull(response.getBody());
                assertTrue(response.getBody().contains("data"));
        }

        @Test
        @Order(4)
        @DisplayName("功能测试 - 保存行程规划 - 无效Token")
        void testSaveItinerary_InvalidToken() throws Exception {
                ItinerarySaveRequest request = ItinerarySaveRequest.builder()
                                .title("测试行程")
                                .totalDays(1)
                                .destination("上海")
                                .dailyPlans(new ArrayList<>())
                                .build();

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                headers.set("token", "invalid_token");
                HttpEntity<ItinerarySaveRequest> httpRequest = new HttpEntity<>(request, headers);

                ResponseEntity<String> response = restTemplate.postForEntity(url("/api/itinerary/save"), httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertTrue(response.getBody().contains("500") || response.getBody().contains("error"));
        }

        @Test
        @Order(5)
        @DisplayName("功能测试 - 更新行程规划")
        void testUpdateItinerary() throws Exception {
                ItineraryPO itinerary = ItineraryPO.builder()
                                .userId(String.valueOf(testUser.getId()))
                                .title("原行程")
                                .build();
                itinerary = itineraryRepository.save(itinerary);

                DayPlan dayPlan = DayPlan.builder()
                                .dayNumber(1)
                                .items(Collections.emptyList())
                                .build();

                ItinerarySaveRequest request = ItinerarySaveRequest.builder()
                                .title("更新后的行程")
                                .estimatedCost(2000.0)
                                .totalDays(2)
                                .destination("上海")
                                .dailyPlans(Arrays.asList(dayPlan))
                                .build();

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                headers.set("token", testToken);
                HttpEntity<ItinerarySaveRequest> httpRequest = new HttpEntity<>(request, headers);

                ResponseEntity<String> response = restTemplate.exchange(
                                url("/api/itinerary/update?itineraryId=" + itinerary.getId()),
                                HttpMethod.PUT,
                                httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertTrue(response.getBody().contains("updated successfully"));
        }

        @Test
        @Order(6)
        @DisplayName("功能测试 - 更新不存在的行程")
        void testUpdateItinerary_NotFound() throws Exception {
                ItinerarySaveRequest request = ItinerarySaveRequest.builder()
                                .title("测试行程")
                                .totalDays(1)
                                .destination("广州")
                                .dailyPlans(new ArrayList<>())
                                .build();

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                headers.set("token", testToken);
                HttpEntity<ItinerarySaveRequest> httpRequest = new HttpEntity<>(request, headers);

                ResponseEntity<String> response = restTemplate.exchange(
                                url("/api/itinerary/update?itineraryId=nonexistent_id"),
                                HttpMethod.PUT,
                                httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @Order(7)
        @DisplayName("功能测试 - 获取行程详情")
        void testGetItineraryDetail() throws Exception {
                ItineraryPO itinerary = ItineraryPO.builder()
                                .userId(String.valueOf(testUser.getId()))
                                .title("测试行程详情")
                                .build();
                itinerary = itineraryRepository.save(itinerary);

                HttpHeaders headers = new HttpHeaders();
                headers.set("token", testToken);
                HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

                ResponseEntity<String> response = restTemplate.exchange(
                                url("/api/itinerary/detail/" + itinerary.getId()),
                                HttpMethod.GET,
                                httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @Order(8)
        @DisplayName("功能测试 - 获取不存在的行程详情")
        void testGetItineraryDetail_NotFound() throws Exception {
                HttpHeaders headers = new HttpHeaders();
                headers.set("token", testToken);
                HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

                ResponseEntity<String> response = restTemplate.exchange(
                                url("/api/itinerary/detail/nonexistent_id"),
                                HttpMethod.GET,
                                httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @Order(9)
        @DisplayName("功能测试 - 获取所有行程")
        void testGetAllItineraries() throws Exception {
                ItineraryPO itinerary1 = ItineraryPO.builder()
                                .userId(String.valueOf(testUser.getId()))
                                .title("行程1")
                                .build();
                itineraryRepository.save(itinerary1);

                ItineraryPO itinerary2 = ItineraryPO.builder()
                                .userId(String.valueOf(testUser.getId()))
                                .title("行程2")
                                .build();
                itineraryRepository.save(itinerary2);

                HttpHeaders headers = new HttpHeaders();
                headers.set("token", testToken);
                HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

                ResponseEntity<String> response = restTemplate.exchange(
                                url("/api/itinerary/getall"),
                                HttpMethod.GET,
                                httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertNotNull(response.getBody());
        }

        @Test
        @Order(10)
        @DisplayName("功能测试 - 根据用户ID获取行程")
        void testGetItinerariesByUserId() throws Exception {
                ItineraryPO itinerary = ItineraryPO.builder()
                                .userId(String.valueOf(testUser.getId()))
                                .title("用户行程")
                                .build();
                itineraryRepository.save(itinerary);

                ResponseEntity<String> response = restTemplate.getForEntity(
                                url("/api/itinerary/user/" + testUser.getId()),
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
                assertNotNull(response.getBody());
        }

        @Test
        @Order(11)
        @DisplayName("功能测试 - 删除行程")
        void testDeleteItinerary() throws Exception {
                ItineraryPO itinerary = ItineraryPO.builder()
                                .userId(String.valueOf(testUser.getId()))
                                .title("待删除行程")
                                .build();
                itinerary = itineraryRepository.save(itinerary);

                HttpHeaders headers = new HttpHeaders();
                headers.set("token", testToken);
                HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

                ResponseEntity<String> response = restTemplate.exchange(
                                url("/api/itinerary/delete?itineraryId=" + itinerary.getId()),
                                HttpMethod.DELETE,
                                httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @Order(12)
        @DisplayName("功能测试 - 删除不存在的行程")
        void testDeleteItinerary_NotFound() throws Exception {
                HttpHeaders headers = new HttpHeaders();
                headers.set("token", testToken);
                HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

                ResponseEntity<String> response = restTemplate.exchange(
                                url("/api/itinerary/delete?itineraryId=nonexistent_id"),
                                HttpMethod.DELETE,
                                httpRequest,
                                String.class);

                assertEquals(HttpStatus.OK, response.getStatusCode());
        }

        @Test
        @Order(13)
        @DisplayName("功能测试 - 完整行程流程：生成->保存->获取->更新->删除")
        void testCompleteItineraryFlow() throws Exception {
                ItinerarySaveRequest saveRequest = ItinerarySaveRequest.builder()
                                .title("完整流程测试行程")
                                .estimatedCost(5000.0)
                                .totalDays(4)
                                .destination("西安")
                                .dailyPlans(new ArrayList<>())
                                .build();

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
                headers.set("token", testToken);

                HttpEntity<ItinerarySaveRequest> saveHttpRequest = new HttpEntity<>(saveRequest, headers);
                ResponseEntity<String> saveResponse = restTemplate.postForEntity(url("/api/itinerary/save"),
                                saveHttpRequest, String.class);
                assertEquals(HttpStatus.OK, saveResponse.getStatusCode());

                HttpEntity<Void> getAllHttpRequest = new HttpEntity<>(headers);
                ResponseEntity<String> getAllResponse = restTemplate.exchange(url("/api/itinerary/getall"),
                                HttpMethod.GET, getAllHttpRequest, String.class);
                assertEquals(HttpStatus.OK, getAllResponse.getStatusCode());

                ItinerarySaveRequest updateRequest = ItinerarySaveRequest.builder()
                                .title("更新后的完整流程行程")
                                .estimatedCost(6000.0)
                                .totalDays(5)
                                .destination("西安")
                                .dailyPlans(new ArrayList<>())
                                .build();

                ItineraryPO savedItinerary = itineraryRepository.findByUserId(String.valueOf(testUser.getId())).stream()
                                .filter(i -> "完整流程测试行程".equals(i.getTitle()))
                                .findFirst()
                                .orElse(null);

                if (savedItinerary != null) {
                        HttpEntity<ItinerarySaveRequest> updateHttpRequest = new HttpEntity<>(updateRequest, headers);
                        ResponseEntity<String> updateResponse = restTemplate.exchange(
                                        url("/api/itinerary/update?itineraryId=" + savedItinerary.getId()),
                                        HttpMethod.PUT,
                                        updateHttpRequest,
                                        String.class);
                        assertEquals(HttpStatus.OK, updateResponse.getStatusCode());

                        HttpEntity<Void> deleteHttpRequest = new HttpEntity<>(headers);
                        ResponseEntity<String> deleteResponse = restTemplate.exchange(
                                        url("/api/itinerary/delete?itineraryId=" + savedItinerary.getId()),
                                        HttpMethod.DELETE,
                                        deleteHttpRequest,
                                        String.class);
                        assertEquals(HttpStatus.OK, deleteResponse.getStatusCode());
                }
        }
}
