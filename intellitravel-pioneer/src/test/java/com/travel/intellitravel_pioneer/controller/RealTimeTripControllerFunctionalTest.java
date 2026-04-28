package com.travel.intellitravel_pioneer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.intellitravel_pioneer.IntellitravelPioneerApplication;
import com.travel.intellitravel_pioneer.po.ItineraryPO;
import com.travel.intellitravel_pioneer.po.RealTimeTripPO;
import com.travel.intellitravel_pioneer.po.UserPO;
import com.travel.intellitravel_pioneer.repository.ItineraryRepository;
import com.travel.intellitravel_pioneer.repository.RealTimeTripRepository;
import com.travel.intellitravel_pioneer.repository.UserRepository;
import com.travel.intellitravel_pioneer.util.TokenUtil;
import com.travel.intellitravel_pioneer.vo.trip.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = IntellitravelPioneerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional
class RealTimeTripControllerFunctionalTest {

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
    private RealTimeTripRepository realTimeTripRepository;

    @Autowired
    private TokenUtil tokenUtil;

    private static final String TEST_USERNAME = "realtime_test_user";
    private static final String TEST_PASSWORD = "testPassword123";
    private static final String TEST_EMAIL = "realtime@test.com";

    private UserPO testUser;
    private String testToken;
    private ItineraryPO testItinerary;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        testUser = new UserPO();
        testUser.setUsername(TEST_USERNAME);
        testUser.setPassword(TEST_PASSWORD);
        testUser.setEmail(TEST_EMAIL);
        testUser = userRepository.save(testUser);

        testToken = tokenUtil.getToken(testUser);

        testItinerary = new ItineraryPO();
        testItinerary.setId(UUID.randomUUID().toString());
        testItinerary.setUserId(String.valueOf(testUser.getId()));
        testItinerary.setTitle("测试行程");
        testItinerary = itineraryRepository.save(testItinerary);
    }

    private Location createLocation(double lat, double lng) {
        Location location = new Location();
        location.setLat(lat);
        location.setLng(lng);
        return location;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    @Order(1)
    @DisplayName("功能测试 - 创建行程")
    void testCreateTrip() throws Exception {
        UserRealTimeStatus initialStatus = new UserRealTimeStatus();
        initialStatus.setTimestamp(OffsetDateTime.now());
        initialStatus.setHeartRate(80);
        initialStatus.setSteps(0);
        initialStatus.setTotalPower(100);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set("token", testToken);
        HttpEntity<UserRealTimeStatus> httpRequest = new HttpEntity<>(initialStatus, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/api/trips?itinerary_id=" + testItinerary.getId()),
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("data"));
    }

    @Test
    @Order(2)
    @DisplayName("功能测试 - 创建行程 - 无效Token")
    void testCreateTrip_InvalidToken() throws Exception {
        UserRealTimeStatus initialStatus = new UserRealTimeStatus();
        initialStatus.setTimestamp(OffsetDateTime.now());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set("token", "invalid_token");
        HttpEntity<UserRealTimeStatus> httpRequest = new HttpEntity<>(initialStatus, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/api/trips?itinerary_id=" + testItinerary.getId()),
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @Order(3)
    @DisplayName("功能测试 - 获取当前行程")
    void testGetCurrentTrip() throws Exception {
        RealTimeTripPO trip = new RealTimeTripPO();
        trip.setTripId(UUID.randomUUID().toString().replace("-", ""));
        trip.setUserId(String.valueOf(testUser.getId()));
        trip.setItineraryId(testItinerary.getId());
        trip.setIsCompleted(false);
        realTimeTripRepository.save(trip);

        HttpHeaders headers = new HttpHeaders();
        headers.set("token", testToken);
        HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/trips/current"),
                HttpMethod.GET,
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @Order(4)
    @DisplayName("功能测试 - 获取当前行程 - 无进行中行程")
    void testGetCurrentTrip_NoCurrentTrip() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", testToken);
        HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/trips/current"),
                HttpMethod.GET,
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @Order(5)
    @DisplayName("功能测试 - 上报实时状态")
    void testUpdateTripStatus() throws Exception {
        RealTimeTripPO trip = new RealTimeTripPO();
        trip.setTripId(UUID.randomUUID().toString().replace("-", ""));
        trip.setUserId(String.valueOf(testUser.getId()));
        trip.setItineraryId(testItinerary.getId());
        trip.setIsCompleted(false);
        trip = realTimeTripRepository.save(trip);

        UserRealTimeStatus status = new UserRealTimeStatus();
        status.setTimestamp(OffsetDateTime.now());
        status.setHeartRate(85);
        status.setSteps(100);
        status.setLocation(createLocation(39.90923, 116.397428));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set("token", testToken);
        HttpEntity<UserRealTimeStatus> httpRequest = new HttpEntity<>(status, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/api/trips/" + trip.getId() + "/status"),
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @Order(6)
    @DisplayName("功能测试 - 上报实时状态 - 行程不存在")
    void testUpdateTripStatus_TripNotFound() throws Exception {
        UserRealTimeStatus status = new UserRealTimeStatus();
        status.setTimestamp(OffsetDateTime.now());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set("token", testToken);
        HttpEntity<UserRealTimeStatus> httpRequest = new HttpEntity<>(status, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/api/trips/nonexistent_trip/status"),
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @Order(7)
    @DisplayName("功能测试 - 结束行程")
    void testEndTrip() throws Exception {
        RealTimeTripPO trip = new RealTimeTripPO();
        trip.setTripId(UUID.randomUUID().toString().replace("-", ""));
        trip.setUserId(String.valueOf(testUser.getId()));
        trip.setItineraryId(testItinerary.getId());
        trip.setIsCompleted(false);
        trip = realTimeTripRepository.save(trip);

        HttpHeaders headers = new HttpHeaders();
        headers.set("token", testToken);
        HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/api/trips/" + trip.getId() + "/end"),
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @Order(8)
    @DisplayName("功能测试 - 结束行程 - 行程不存在")
    void testEndTrip_TripNotFound() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", testToken);
        HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/api/trips/nonexistent_trip/end"),
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @Order(9)
    @DisplayName("功能测试 - 获取行程轨迹图")
    void testGetTrajectoryMap() throws Exception {
        RealTimeTripPO trip = new RealTimeTripPO();
        trip.setTripId(UUID.randomUUID().toString().replace("-", ""));
        trip.setUserId(String.valueOf(testUser.getId()));
        trip.setItineraryId(testItinerary.getId());
        trip.setIsCompleted(true);
        trip = realTimeTripRepository.save(trip);

        HttpHeaders headers = new HttpHeaders();
        headers.set("token", testToken);
        HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/trips/" + trip.getId() + "/trajectory-map"),
                HttpMethod.GET,
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @Order(10)
    @DisplayName("功能测试 - 生成行程战报")
    void testGenerateTripReport() throws Exception {
        RealTimeTripPO trip = new RealTimeTripPO();
        trip.setTripId(UUID.randomUUID().toString().replace("-", ""));
        trip.setUserId(String.valueOf(testUser.getId()));
        trip.setItineraryId(testItinerary.getId());
        trip.setIsCompleted(true);
        trip = realTimeTripRepository.save(trip);

        GenerateTripReportRequest request = new GenerateTripReportRequest();
        request.setStyle("nature");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set("token", testToken);
        HttpEntity<GenerateTripReportRequest> httpRequest = new HttpEntity<>(request, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/api/trips/" + trip.getId() + "/report"),
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @Order(11)
    @DisplayName("功能测试 - 查询行程战报列表")
    void testListTripReports() throws Exception {
        RealTimeTripPO trip = new RealTimeTripPO();
        trip.setTripId(UUID.randomUUID().toString().replace("-", ""));
        trip.setUserId(String.valueOf(testUser.getId()));
        trip.setItineraryId(testItinerary.getId());
        trip.setIsCompleted(true);
        trip = realTimeTripRepository.save(trip);

        HttpHeaders headers = new HttpHeaders();
        headers.set("token", testToken);
        HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/trips/" + trip.getId() + "/reports?page=0&size=10"),
                HttpMethod.GET,
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @Order(12)
    @DisplayName("功能测试 - 生成行程AI总结")
    void testGenerateTripSummary() throws Exception {
        RealTimeTripPO trip = new RealTimeTripPO();
        trip.setTripId(UUID.randomUUID().toString().replace("-", ""));
        trip.setUserId(String.valueOf(testUser.getId()));
        trip.setItineraryId(testItinerary.getId());
        trip.setIsCompleted(true);
        trip = realTimeTripRepository.save(trip);

        HttpHeaders headers = new HttpHeaders();
        headers.set("token", testToken);
        HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/api/trips/" + trip.getId() + "/summary"),
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @Order(13)
    @DisplayName("功能测试 - 获取行程AI总结")
    void testGetTripSummary() throws Exception {
        RealTimeTripPO trip = new RealTimeTripPO();
        trip.setTripId(UUID.randomUUID().toString().replace("-", ""));
        trip.setUserId(String.valueOf(testUser.getId()));
        trip.setItineraryId(testItinerary.getId());
        trip.setIsCompleted(true);
        trip = realTimeTripRepository.save(trip);

        HttpHeaders headers = new HttpHeaders();
        headers.set("token", testToken);
        HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/trips/" + trip.getId() + "/summary"),
                HttpMethod.GET,
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @Order(14)
    @DisplayName("功能测试 - 查询历史行程总结列表")
    void testListTripSummaries() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", testToken);
        HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/trips/summaries?page=0&size=10"),
                HttpMethod.GET,
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @Order(15)
    @DisplayName("功能测试 - 完整行程流程：创建->上报状态->结束->获取轨迹图")
    void testCompleteTripFlow() throws Exception {
        UserRealTimeStatus initialStatus = new UserRealTimeStatus();
        initialStatus.setTimestamp(OffsetDateTime.now());
        initialStatus.setHeartRate(80);
        initialStatus.setSteps(0);
        initialStatus.setTotalPower(100);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set("token", testToken);

        HttpEntity<UserRealTimeStatus> createRequest = new HttpEntity<>(initialStatus, headers);
        ResponseEntity<String> createResponse = restTemplate.postForEntity(
                url("/api/trips?itinerary_id=" + testItinerary.getId()),
                createRequest,
                String.class);
        assertEquals(HttpStatus.OK, createResponse.getStatusCode());

        HttpEntity<Void> getCurrentRequest = new HttpEntity<>(headers);
        ResponseEntity<String> getCurrentResponse = restTemplate.exchange(
                url("/api/trips/current"),
                HttpMethod.GET,
                getCurrentRequest,
                String.class);
        assertEquals(HttpStatus.OK, getCurrentResponse.getStatusCode());

        RealTimeTripPO trip = realTimeTripRepository.findFirstByUserIdAndIsCompletedFalseOrderByUpdatedAtDesc(String.valueOf(testUser.getId()));

        if (trip != null) {
            UserRealTimeStatus status = new UserRealTimeStatus();
            status.setTimestamp(OffsetDateTime.now());
            status.setHeartRate(90);
            status.setSteps(500);
            status.setLocation(createLocation(39.91923, 116.407428));

            HttpEntity<UserRealTimeStatus> statusRequest = new HttpEntity<>(status, headers);
            ResponseEntity<String> statusResponse = restTemplate.postForEntity(
                    url("/api/trips/" + trip.getId() + "/status"),
                    statusRequest,
                    String.class);
            assertEquals(HttpStatus.OK, statusResponse.getStatusCode());

            HttpEntity<Void> endRequest = new HttpEntity<>(headers);
            ResponseEntity<String> endResponse = restTemplate.postForEntity(
                    url("/api/trips/" + trip.getId() + "/end"),
                    endRequest,
                    String.class);
            assertEquals(HttpStatus.OK, endResponse.getStatusCode());

            HttpEntity<Void> trajectoryRequest = new HttpEntity<>(headers);
            ResponseEntity<String> trajectoryResponse = restTemplate.exchange(
                    url("/api/trips/" + trip.getId() + "/trajectory-map"),
                    HttpMethod.GET,
                    trajectoryRequest,
                    String.class);
            assertEquals(HttpStatus.OK, trajectoryResponse.getStatusCode());
        }
    }
}
