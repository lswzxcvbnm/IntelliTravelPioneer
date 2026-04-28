package com.travel.intellitravel_pioneer.service.serviceImpl;

import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import com.travel.intellitravel_pioneer.po.RealTimeTripPO;
import com.travel.intellitravel_pioneer.po.TripTrajectoryMapPO;
import com.travel.intellitravel_pioneer.po.TripTrajectoryPointPO;
import com.travel.intellitravel_pioneer.po.UserHealthPO;
import com.travel.intellitravel_pioneer.po.UserPO;
import com.travel.intellitravel_pioneer.repository.RealTimeTripRepository;
import com.travel.intellitravel_pioneer.repository.TripTrajectoryMapRepository;
import com.travel.intellitravel_pioneer.repository.TripTrajectoryPointRepository;
import com.travel.intellitravel_pioneer.repository.UserHealthRepository;
import com.travel.intellitravel_pioneer.service.AmapService;
import com.travel.intellitravel_pioneer.util.TokenUtil;
import com.travel.intellitravel_pioneer.vo.trip.CreateTripResponse;
import com.travel.intellitravel_pioneer.vo.trip.CurrentTripResponse;
import com.travel.intellitravel_pioneer.vo.trip.Location;
import com.travel.intellitravel_pioneer.vo.trip.UserRealTimeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RealTimeTripServiceImplTest {

    @Mock
    private UserHealthRepository userHealthRepository;

    @Mock
    private RealTimeTripRepository realTimeTripRepository;

    @Mock
    private TripTrajectoryMapRepository tripTrajectoryMapRepository;

    @Mock
    private TripTrajectoryPointRepository tripTrajectoryPointRepository;

    @Mock
    private AmapService amapService;

    @Mock
    private TokenUtil tokenUtil;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private RealTimeTripServiceImpl realTimeTripService;

    private String testToken;
    private UserPO testUser;
    private UserRealTimeStatus testStatus;
    private RealTimeTripPO testTripPO;
    private TripTrajectoryMapPO testMapPO;

    @BeforeEach
    void setUp() {
        testToken = "test-token";
        testUser = new UserPO();
        testUser.setId(1);
        testUser.setUsername("testuser");

        Location location = new Location();
        location.setLat(39.9042);
        location.setLng(116.4074);

        testStatus = new UserRealTimeStatus();
        testStatus.setTimestamp(OffsetDateTime.now());
        testStatus.setHeartRate(90);
        testStatus.setSteps(1000);
        testStatus.setLocation(location);

        testTripPO = new RealTimeTripPO();
        testTripPO.setTripId("test-trip-id");
        testTripPO.setUserId("1");
        testTripPO.setItineraryId("test-itinerary-id");
        testTripPO.setIsCompleted(false);
        testTripPO.setLastStatus(testStatus);

        testMapPO = new TripTrajectoryMapPO();
        testMapPO.setTripId("test-trip-id");
        testMapPO.setUserId("1");
        testMapPO.setItineraryId("test-itinerary-id");
    }

    @Test
    @DisplayName("创建行程 - 成功创建")
    void testCreateTrip_Success() {
        when(tokenUtil.getUser(testToken)).thenReturn(testUser);
        when(realTimeTripRepository.findFirstByUserIdAndIsCompletedFalseOrderByUpdatedAtDesc("1")).thenReturn(null);
        when(userHealthRepository.findByUserId(1)).thenReturn(null);
        when(userHealthRepository.save(any(UserHealthPO.class))).thenAnswer(invocation -> {
            UserHealthPO po = invocation.getArgument(0);
            po.setCombatPower(1000);
            return po;
        });
        when(realTimeTripRepository.save(any(RealTimeTripPO.class))).thenReturn(testTripPO);
        when(tripTrajectoryMapRepository.findByTripId(any())).thenReturn(null);
        when(tripTrajectoryMapRepository.save(any(TripTrajectoryMapPO.class))).thenReturn(testMapPO);

        CreateTripResponse response = realTimeTripService.createTrip(testToken, "test-itinerary-id", testStatus);

        assertNotNull(response);
        assertNotNull(response.getTripId());
        assertEquals("行程创建成功", response.getMessage());
    }

    @Test
    @DisplayName("创建行程 - Token为空抛出异常")
    void testCreateTrip_NullToken() {
        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> realTimeTripService.createTrip(null, "test-itinerary-id", testStatus));

        assertEquals("token is required", exception.getMessage());
    }

    @Test
    @DisplayName("创建行程 - Token为空白抛出异常")
    void testCreateTrip_BlankToken() {
        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> realTimeTripService.createTrip("   ", "test-itinerary-id", testStatus));

        assertEquals("token is required", exception.getMessage());
    }

    @Test
    @DisplayName("创建行程 - 行程ID为空抛出异常")
    void testCreateTrip_NullItineraryId() {
        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> realTimeTripService.createTrip(testToken, null, testStatus));

        assertEquals("itinerary_id is required", exception.getMessage());
    }

    @Test
    @DisplayName("创建行程 - 初始状态为空抛出异常")
    void testCreateTrip_NullInitialStatus() {
        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> realTimeTripService.createTrip(testToken, "test-itinerary-id", null));

        assertEquals("initial status body is required", exception.getMessage());
    }

    @Test
    @DisplayName("创建行程 - 位置信息缺失抛出异常")
    void testCreateTrip_MissingLocation() {
        testStatus.setLocation(null);

        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> realTimeTripService.createTrip(testToken, "test-itinerary-id", testStatus));

        assertEquals("location.lat and location.lng are required", exception.getMessage());
    }

    @Test
    @DisplayName("创建行程 - 存在未结束行程抛出异常")
    void testCreateTrip_ActiveTripExists() {
        when(tokenUtil.getUser(testToken)).thenReturn(testUser);
        when(realTimeTripRepository.findFirstByUserIdAndIsCompletedFalseOrderByUpdatedAtDesc("1")).thenReturn(testTripPO);

        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> realTimeTripService.createTrip(testToken, "test-itinerary-id", testStatus));

        assertEquals("存在未结束行程，请先结束后再创建", exception.getMessage());
    }

    @Test
    @DisplayName("获取当前行程 - 成功获取")
    void testGetCurrentTrip_Success() {
        when(tokenUtil.getUser(testToken)).thenReturn(testUser);
        when(realTimeTripRepository.findFirstByUserIdAndIsCompletedFalseOrderByUpdatedAtDesc("1")).thenReturn(testTripPO);

        CurrentTripResponse response = realTimeTripService.getCurrentTrip(testToken);

        assertNotNull(response);
        assertTrue(response.getHasActiveTrip());
        assertEquals("test-trip-id", response.getTripId());
        assertEquals("test-itinerary-id", response.getItineraryId());
    }

    @Test
    @DisplayName("获取当前行程 - 无活跃行程")
    void testGetCurrentTrip_NoActiveTrip() {
        when(tokenUtil.getUser(testToken)).thenReturn(testUser);
        when(realTimeTripRepository.findFirstByUserIdAndIsCompletedFalseOrderByUpdatedAtDesc("1")).thenReturn(null);

        CurrentTripResponse response = realTimeTripService.getCurrentTrip(testToken);

        assertNotNull(response);
        assertFalse(response.getHasActiveTrip());
        assertNull(response.getTripId());
    }

    @Test
    @DisplayName("获取当前行程 - Token为空抛出异常")
    void testGetCurrentTrip_NullToken() {
        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> realTimeTripService.getCurrentTrip(null));

        assertEquals("token is required", exception.getMessage());
    }

    @Test
    @DisplayName("上报行程状态 - 成功上报")
    void testReportTripStatus_Success() {
        when(tokenUtil.getUser(testToken)).thenReturn(testUser);
        when(tripTrajectoryMapRepository.findByTripIdAndUserId("test-trip-id", "1")).thenReturn(testMapPO);
        when(realTimeTripRepository.findByTripId("test-trip-id")).thenReturn(testTripPO);
        when(realTimeTripRepository.save(any(RealTimeTripPO.class))).thenReturn(testTripPO);

        String result = realTimeTripService.reportTripStatus(testToken, "test-trip-id", testStatus);

        assertEquals("状态上报成功", result);
    }

    @Test
    @DisplayName("上报行程状态 - Token为空抛出异常")
    void testReportTripStatus_NullToken() {
        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> realTimeTripService.reportTripStatus(null, "test-trip-id", testStatus));

        assertEquals("token is required", exception.getMessage());
    }

    @Test
    @DisplayName("上报行程状态 - 行程ID为空抛出异常")
    void testReportTripStatus_NullTripId() {
        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> realTimeTripService.reportTripStatus(testToken, null, testStatus));

        assertEquals("trip_id is required", exception.getMessage());
    }

    @Test
    @DisplayName("上报行程状态 - 状态体为空抛出异常")
    void testReportTripStatus_NullStatus() {
        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> realTimeTripService.reportTripStatus(testToken, "test-trip-id", null));

        assertEquals("status body is required", exception.getMessage());
    }

    @Test
    @DisplayName("上报行程状态 - 时间戳缺失抛出异常")
    void testReportTripStatus_MissingTimestamp() {
        testStatus.setTimestamp(null);

        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> realTimeTripService.reportTripStatus(testToken, "test-trip-id", testStatus));

        assertEquals("timestamp is required", exception.getMessage());
    }

    @Test
    @DisplayName("上报行程状态 - 心率无效抛出异常")
    void testReportTripStatus_InvalidHeartRate() {
        testStatus.setHeartRate(0);

        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> realTimeTripService.reportTripStatus(testToken, "test-trip-id", testStatus));

        assertEquals("heart_rate must be positive", exception.getMessage());
    }

    @Test
    @DisplayName("上报行程状态 - 步数为负数抛出异常")
    void testReportTripStatus_NegativeSteps() {
        testStatus.setSteps(-1);

        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> realTimeTripService.reportTripStatus(testToken, "test-trip-id", testStatus));

        assertEquals("steps must be greater than or equal to 0", exception.getMessage());
    }

    @Test
    @DisplayName("上报行程状态 - 位置信息缺失抛出异常")
    void testReportTripStatus_MissingLocation() {
        testStatus.setLocation(null);

        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> realTimeTripService.reportTripStatus(testToken, "test-trip-id", testStatus));

        assertEquals("location.lat and location.lng are required", exception.getMessage());
    }

    @Test
    @DisplayName("上报行程状态 - 行程不存在抛出异常")
    void testReportTripStatus_TripNotFound() {
        when(tokenUtil.getUser(testToken)).thenReturn(testUser);
        when(tripTrajectoryMapRepository.findByTripIdAndUserId("test-trip-id", "1")).thenReturn(null);

        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> realTimeTripService.reportTripStatus(testToken, "test-trip-id", testStatus));

        assertEquals("trip not found", exception.getMessage());
    }

    @Test
    @DisplayName("结束行程 - 成功结束")
    void testEndTrip_Success() {
        when(tokenUtil.getUser(testToken)).thenReturn(testUser);
        when(tripTrajectoryMapRepository.findByTripIdAndUserId("test-trip-id", "1")).thenReturn(testMapPO);
        when(realTimeTripRepository.findByTripId("test-trip-id")).thenReturn(testTripPO);
        when(realTimeTripRepository.save(any(RealTimeTripPO.class))).thenReturn(testTripPO);
        when(tripTrajectoryPointRepository.findByTripIdOrderBySeqAsc("test-trip-id")).thenReturn(new ArrayList<>());
        when(tripTrajectoryMapRepository.findByTripId("test-trip-id")).thenReturn(testMapPO);

        String result = realTimeTripService.endTrip(testToken, "test-trip-id");

        assertEquals("行程已结束", result);
    }

    @Test
    @DisplayName("结束行程 - Token为空抛出异常")
    void testEndTrip_NullToken() {
        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> realTimeTripService.endTrip(null, "test-trip-id"));

        assertEquals("token is required", exception.getMessage());
    }

    @Test
    @DisplayName("结束行程 - 行程ID为空抛出异常")
    void testEndTrip_NullTripId() {
        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> realTimeTripService.endTrip(testToken, null));

        assertEquals("trip_id is required", exception.getMessage());
    }

    @Test
    @DisplayName("结束行程 - 行程不存在抛出异常")
    void testEndTrip_TripNotFound() {
        when(tokenUtil.getUser(testToken)).thenReturn(testUser);
        when(tripTrajectoryMapRepository.findByTripIdAndUserId("test-trip-id", "1")).thenReturn(null);

        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> realTimeTripService.endTrip(testToken, "test-trip-id"));

        assertEquals("trip not found", exception.getMessage());
    }

    @Test
    @DisplayName("获取轨迹地图 - 成功获取")
    void testGetTrajectoryMap_Success() {
        testMapPO.setTrajectoryMapUrl("http://example.com/map.png");
        when(tokenUtil.getUser(testToken)).thenReturn(testUser);
        when(tripTrajectoryMapRepository.findByTripIdAndUserId("test-trip-id", "1")).thenReturn(testMapPO);

        String result = realTimeTripService.getTrajectoryMap(testToken, "test-trip-id");

        assertEquals("http://example.com/map.png", result);
    }

    @Test
    @DisplayName("获取轨迹地图 - Token为空抛出异常")
    void testGetTrajectoryMap_NullToken() {
        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> realTimeTripService.getTrajectoryMap(null, "test-trip-id"));

        assertEquals("token is required", exception.getMessage());
    }

    @Test
    @DisplayName("获取轨迹地图 - 行程ID为空抛出异常")
    void testGetTrajectoryMap_NullTripId() {
        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> realTimeTripService.getTrajectoryMap(testToken, null));

        assertEquals("trip_id is required", exception.getMessage());
    }

    @Test
    @DisplayName("获取轨迹地图 - 行程不存在抛出异常")
    void testGetTrajectoryMap_TripNotFound() {
        when(tokenUtil.getUser(testToken)).thenReturn(testUser);
        when(tripTrajectoryMapRepository.findByTripIdAndUserId("test-trip-id", "1")).thenReturn(null);

        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> realTimeTripService.getTrajectoryMap(testToken, "test-trip-id"));

        assertEquals("trip not found", exception.getMessage());
    }

    @Test
    @DisplayName("获取轨迹地图 - 地图未生成抛出异常")
    void testGetTrajectoryMap_MapNotGenerated() {
        testMapPO.setTrajectoryMapUrl(null);
        when(tokenUtil.getUser(testToken)).thenReturn(testUser);
        when(tripTrajectoryMapRepository.findByTripIdAndUserId("test-trip-id", "1")).thenReturn(testMapPO);

        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> realTimeTripService.getTrajectoryMap(testToken, "test-trip-id"));

        assertEquals("trajectory map not generated", exception.getMessage());
    }

    @Test
    @DisplayName("无效Token抛出异常")
    void testInvalidToken() {
        when(tokenUtil.getUser(testToken)).thenReturn(null);

        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> realTimeTripService.getCurrentTrip(testToken));

        assertEquals("invalid token", exception.getMessage());
    }
}
