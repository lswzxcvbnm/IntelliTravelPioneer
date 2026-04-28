package com.travel.intellitravel_pioneer.service.serviceImpl;

import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import com.travel.intellitravel_pioneer.po.ItineraryPO;
import com.travel.intellitravel_pioneer.po.UserPO;
import com.travel.intellitravel_pioneer.repository.ItineraryRepository;
import com.travel.intellitravel_pioneer.service.ai.TripPlanningAiClient;
import com.travel.intellitravel_pioneer.util.TokenUtil;
import com.travel.intellitravel_pioneer.vo.itinerary.DayPlan;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryPlan;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryRequest;
import com.travel.intellitravel_pioneer.vo.itinerary.ItinerarySaveRequest;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItineraryServiceImplTest {

    @Mock
    private ItineraryRepository itineraryRepository;

    @Mock
    private TripPlanningAiClient tripPlanningAiClient;

    @Mock
    private TokenUtil tokenUtil;

    @InjectMocks
    private ItineraryServiceImpl itineraryService;

    private String testToken;
    private String testUserId;
    private UserPO testUser;
    private ItineraryPO testItineraryPO;
    private ItineraryPlan testItineraryPlan;
    private ItinerarySaveRequest testSaveRequest;
    private ItineraryRequest testRequest;

    @BeforeEach
    void setUp() {
        testToken = "test-token";
        testUserId = "1";

        testUser = new UserPO();
        testUser.setId(1);
        testUser.setUsername("testuser");

        List<DayPlan> dailyPlans = new ArrayList<>();
        DayPlan dayPlan = DayPlan.builder()
                .dayNumber(1)
                .items(new ArrayList<>())
                .build();
        dailyPlans.add(dayPlan);

        testItineraryPlan = ItineraryPlan.builder()
                .title("Test Trip")
                .estimatedCost(1000.0)
                .totalDays(3)
                .destination("Beijing")
                .dailyPlans(dailyPlans)
                .build();

        testItineraryPO = ItineraryPO.builder()
                .id("test-itinerary-id")
                .userId(testUserId)
                .title("Test Trip")
                .plan(testItineraryPlan)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        testSaveRequest = ItinerarySaveRequest.builder()
                .title("Test Trip")
                .estimatedCost(1000.0)
                .totalDays(3)
                .destination("Beijing")
                .dailyPlans(dailyPlans)
                .build();

        testRequest = new ItineraryRequest();
        testRequest.setDestination("Beijing");
        testRequest.setDays(3);
    }

    @Test
    @DisplayName("生成行程规划 - 成功生成")
    void testGenerateItinerary_Success() {
        when(tripPlanningAiClient.generateItinerary(eq(testToken), eq(testRequest), any()))
                .thenReturn(testItineraryPlan);

        ItineraryPlan result = itineraryService.generateItinerary(testToken, testRequest);

        assertNotNull(result);
        assertEquals("Test Trip", result.getTitle());
        assertEquals("Beijing", result.getDestination());
        assertEquals(3, result.getTotalDays());
    }

    @Test
    @DisplayName("生成行程规划 - Token为空抛出异常")
    void testGenerateItinerary_NullToken() {
        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> itineraryService.generateItinerary(null, testRequest));

        assertEquals("token is required", exception.getMessage());
    }

    @Test
    @DisplayName("生成行程规划 - Token为空白抛出异常")
    void testGenerateItinerary_BlankToken() {
        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> itineraryService.generateItinerary("   ", testRequest));

        assertEquals("token is required", exception.getMessage());
    }

    @Test
    @DisplayName("生成行程规划 - 请求体为空抛出异常")
    void testGenerateItinerary_NullRequest() {
        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> itineraryService.generateItinerary(testToken, null));

        assertEquals("request body is required", exception.getMessage());
    }

    @Test
    @DisplayName("生成行程规划 - AI客户端异常抛出异常")
    void testGenerateItinerary_AiClientException() {
        when(tripPlanningAiClient.generateItinerary(eq(testToken), eq(testRequest), any()))
                .thenThrow(new RuntimeException("AI service error"));

        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> itineraryService.generateItinerary(testToken, testRequest));

        assertTrue(exception.getMessage().contains("Failed to generate itinerary"));
    }

    @Test
    @DisplayName("保存行程规划 - 成功保存")
    void testSaveItinerary_Success() {
        when(tokenUtil.getUser(testToken)).thenReturn(testUser);
        when(itineraryRepository.save(any(ItineraryPO.class))).thenReturn(testItineraryPO);

        String result = itineraryService.saveItinerary(testToken, testSaveRequest);

        assertNotNull(result);
        verify(itineraryRepository, times(1)).save(any(ItineraryPO.class));
    }

    @Test
    @DisplayName("保存行程规划 - Token为空抛出异常")
    void testSaveItinerary_NullToken() {
        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> itineraryService.saveItinerary(null, testSaveRequest));

        assertEquals("token is required", exception.getMessage());
    }

    @Test
    @DisplayName("保存行程规划 - 请求体为空抛出异常")
    void testSaveItinerary_NullRequest() {
        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> itineraryService.saveItinerary(testToken, null));

        assertEquals("request body is required", exception.getMessage());
    }

    @Test
    @DisplayName("更新行程规划 - 成功更新")
    void testUpdateItinerary_Success() {
        when(tokenUtil.getUser(testToken)).thenReturn(testUser);
        when(itineraryRepository.findById("test-itinerary-id")).thenReturn(Optional.of(testItineraryPO));
        when(itineraryRepository.save(any(ItineraryPO.class))).thenReturn(testItineraryPO);

        assertDoesNotThrow(() -> itineraryService.updateItinerary(testToken, "test-itinerary-id", testSaveRequest));

        verify(itineraryRepository, times(1)).save(any(ItineraryPO.class));
    }

    @Test
    @DisplayName("更新行程规划 - Token为空抛出异常")
    void testUpdateItinerary_NullToken() {
        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> itineraryService.updateItinerary(null, "test-itinerary-id", testSaveRequest));

        assertEquals("token is required", exception.getMessage());
    }

    @Test
    @DisplayName("更新行程规划 - 行程ID为空抛出异常")
    void testUpdateItinerary_NullItineraryId() {
        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> itineraryService.updateItinerary(testToken, null, testSaveRequest));

        assertEquals("itineraryId is required", exception.getMessage());
    }

    @Test
    @DisplayName("更新行程规划 - 请求体为空抛出异常")
    void testUpdateItinerary_NullRequest() {
        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> itineraryService.updateItinerary(testToken, "test-itinerary-id", null));

        assertEquals("plan data is required", exception.getMessage());
    }

    @Test
    @DisplayName("更新行程规划 - 行程不存在抛出异常")
    void testUpdateItinerary_ItineraryNotFound() {
        when(tokenUtil.getUser(testToken)).thenReturn(testUser);
        when(itineraryRepository.findById("nonexistent-id")).thenReturn(Optional.empty());

        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> itineraryService.updateItinerary(testToken, "nonexistent-id", testSaveRequest));

        assertEquals("itinerary not found", exception.getMessage());
    }

    @Test
    @DisplayName("更新行程规划 - 无权限更新抛出异常")
    void testUpdateItinerary_AccessDenied() {
        UserPO otherUser = new UserPO();
        otherUser.setId(999);
        when(tokenUtil.getUser(testToken)).thenReturn(otherUser);
        when(itineraryRepository.findById("test-itinerary-id")).thenReturn(Optional.of(testItineraryPO));

        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> itineraryService.updateItinerary(testToken, "test-itinerary-id", testSaveRequest));

        assertEquals("access denied: itinerary does not belong to user", exception.getMessage());
    }

    @Test
    @DisplayName("获取行程详情 - 成功获取")
    void testGetItineraryDetail_Success() {
        when(itineraryRepository.findById("test-itinerary-id")).thenReturn(Optional.of(testItineraryPO));

        ItineraryVO result = itineraryService.getItineraryDetail(testToken, "test-itinerary-id");

        assertNotNull(result);
        assertEquals("test-itinerary-id", result.getId());
        assertEquals("Test Trip", result.getTitle());
        assertEquals("Beijing", result.getDestination());
    }

    @Test
    @DisplayName("获取行程详情 - Token为空抛出异常")
    void testGetItineraryDetail_NullToken() {
        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> itineraryService.getItineraryDetail(null, "test-itinerary-id"));

        assertEquals("token is required", exception.getMessage());
    }

    @Test
    @DisplayName("获取行程详情 - 行程ID为空抛出异常")
    void testGetItineraryDetail_NullItineraryId() {
        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> itineraryService.getItineraryDetail(testToken, null));

        assertEquals("itineraryId is required", exception.getMessage());
    }

    @Test
    @DisplayName("获取行程详情 - 行程不存在抛出异常")
    void testGetItineraryDetail_ItineraryNotFound() {
        when(itineraryRepository.findById("nonexistent-id")).thenReturn(Optional.empty());

        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> itineraryService.getItineraryDetail(testToken, "nonexistent-id"));

        assertEquals("itinerary not found", exception.getMessage());
    }

    @Test
    @DisplayName("获取所有行程 - 成功获取")
    void testGetAllItineraries_Success() {
        List<ItineraryPO> itineraryList = new ArrayList<>();
        itineraryList.add(testItineraryPO);

        when(tokenUtil.getUser(testToken)).thenReturn(testUser);
        when(itineraryRepository.findByUserId(testUserId)).thenReturn(itineraryList);

        List<String> result = itineraryService.getAllItineraries(testToken);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("test-itinerary-id", result.get(0));
    }

    @Test
    @DisplayName("获取所有行程 - Token为空抛出异常")
    void testGetAllItineraries_NullToken() {
        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> itineraryService.getAllItineraries(null));

        assertEquals("token is required", exception.getMessage());
    }

    @Test
    @DisplayName("获取所有行程 - 空列表")
    void testGetAllItineraries_EmptyList() {
        when(tokenUtil.getUser(testToken)).thenReturn(testUser);
        when(itineraryRepository.findByUserId(testUserId)).thenReturn(new ArrayList<>());

        List<String> result = itineraryService.getAllItineraries(testToken);

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    @DisplayName("根据用户ID获取行程 - 成功获取")
    void testGetItinerariesByUserId_Success() {
        List<ItineraryPO> itineraryList = new ArrayList<>();
        itineraryList.add(testItineraryPO);

        when(itineraryRepository.findByUserId(testUserId)).thenReturn(itineraryList);

        List<String> result = itineraryService.getItinerariesByUserId(testUserId);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("test-itinerary-id", result.get(0));
    }

    @Test
    @DisplayName("根据用户ID获取行程 - 用户ID为空抛出异常")
    void testGetItinerariesByUserId_NullUserId() {
        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> itineraryService.getItinerariesByUserId(null));

        assertEquals("userId is required", exception.getMessage());
    }

    @Test
    @DisplayName("删除行程 - 成功删除")
    void testDeleteItinerary_Success() {
        when(tokenUtil.getUser(testToken)).thenReturn(testUser);
        when(itineraryRepository.findById("test-itinerary-id")).thenReturn(Optional.of(testItineraryPO));

        assertDoesNotThrow(() -> itineraryService.deleteItinerary(testToken, "test-itinerary-id"));

        verify(itineraryRepository, times(1)).deleteById("test-itinerary-id");
    }

    @Test
    @DisplayName("删除行程 - Token为空抛出异常")
    void testDeleteItinerary_NullToken() {
        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> itineraryService.deleteItinerary(null, "test-itinerary-id"));

        assertEquals("token is required", exception.getMessage());
    }

    @Test
    @DisplayName("删除行程 - 行程ID为空抛出异常")
    void testDeleteItinerary_NullItineraryId() {
        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> itineraryService.deleteItinerary(testToken, null));

        assertEquals("itineraryId is required", exception.getMessage());
    }

    @Test
    @DisplayName("删除行程 - 行程不存在抛出异常")
    void testDeleteItinerary_ItineraryNotFound() {
        when(tokenUtil.getUser(testToken)).thenReturn(testUser);
        when(itineraryRepository.findById("nonexistent-id")).thenReturn(Optional.empty());

        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> itineraryService.deleteItinerary(testToken, "nonexistent-id"));

        assertEquals("itinerary not found", exception.getMessage());
    }

    @Test
    @DisplayName("删除行程 - 无权限删除抛出异常")
    void testDeleteItinerary_AccessDenied() {
        UserPO otherUser = new UserPO();
        otherUser.setId(999);
        when(tokenUtil.getUser(testToken)).thenReturn(otherUser);
        when(itineraryRepository.findById("test-itinerary-id")).thenReturn(Optional.of(testItineraryPO));

        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> itineraryService.deleteItinerary(testToken, "test-itinerary-id"));

        assertEquals("access denied: itinerary does not belong to user", exception.getMessage());
    }
}
