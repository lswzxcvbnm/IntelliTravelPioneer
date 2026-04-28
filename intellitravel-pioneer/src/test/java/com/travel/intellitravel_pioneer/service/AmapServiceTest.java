package com.travel.intellitravel_pioneer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.intellitravel_pioneer.configure.AmapConfig;
import com.travel.intellitravel_pioneer.vo.trip.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AmapServiceTest {

    @Mock
    private AmapConfig amapConfig;

    @Mock
    private RestTemplate restTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AmapService amapService;

    private String testKey;

    @BeforeEach
    void setUp() {
        testKey = "test-api-key";

        lenient().when(amapConfig.getKey()).thenReturn(testKey);
        lenient().when(amapConfig.getGeocodeUrl()).thenReturn("https://restapi.amap.com/v3/geocode/geo");
        lenient().when(amapConfig.getWeatherUrl()).thenReturn("https://restapi.amap.com/v3/weather/weatherInfo");
        lenient().when(amapConfig.getPlaceTextUrl()).thenReturn("https://restapi.amap.com/v3/place/text");
        lenient().when(amapConfig.getPlaceAroundUrl()).thenReturn("https://restapi.amap.com/v3/place/around");
        lenient().when(amapConfig.getStaticMapUrl()).thenReturn("https://restapi.amap.com/v3/staticmap");
    }

    @Test
    @DisplayName("地理编码 - 成功获取经纬度")
    void testGeocode_Success() throws Exception {
        String mockResponse = "{\"status\":\"1\",\"geocodes\":[{\"location\":\"116.397428,39.90923\"}]}";
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(mockResponse);

        String result = amapService.geocode("北京市朝阳区");

        assertNotNull(result);
        assertEquals("116.397428,39.90923", result);
    }

    @Test
    @DisplayName("地理编码 - 地址为空返回null")
    void testGeocode_NullAddress() {
        String result = amapService.geocode(null);
        assertNull(result);
    }

    @Test
    @DisplayName("地理编码 - 地址为空白返回null")
    void testGeocode_BlankAddress() {
        String result = amapService.geocode("   ");
        assertNull(result);
    }

    @Test
    @DisplayName("地理编码 - API返回空结果返回null")
    void testGeocode_EmptyResult() throws Exception {
        String mockResponse = "{\"status\":\"1\",\"geocodes\":[]}";
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(mockResponse);

        String result = amapService.geocode("不存在的地址");

        assertNull(result);
    }

    @Test
    @DisplayName("获取地理编码信息 - 成功获取")
    void testGetGeocodeInfo_Success() throws Exception {
        String mockResponse = "{\"status\":\"1\",\"geocodes\":[{\"location\":\"116.397428,39.90923\",\"formatted_address\":\"北京市朝阳区\"}]}";
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(mockResponse);

        JsonNode result = amapService.getGeocodeInfo("北京市朝阳区");

        assertNotNull(result);
        assertTrue(result.isArray());
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("获取地理编码信息 - 地址为空返回null")
    void testGetGeocodeInfo_NullAddress() {
        JsonNode result = amapService.getGeocodeInfo(null);
        assertNull(result);
    }

    @Test
    @DisplayName("获取天气信息 - 成功获取")
    void testGetWeatherInfo_Success() throws Exception {
        String mockResponse = "{\"status\":\"1\",\"lives\":[{\"city\":\"北京\",\"weather\":\"晴\"}]}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(mockResponse);

        JsonNode result = amapService.getWeatherInfo("110000", "base");

        assertNotNull(result);
        assertTrue(result.isArray());
        assertTrue(result.size() > 0);
        assertTrue(result.get(0).has("city"));
    }

    @Test
    @DisplayName("获取天气信息 - adcode为空返回null")
    void testGetWeatherInfo_NullAdcode() {
        JsonNode result = amapService.getWeatherInfo(null, "base");
        assertNull(result);
    }

    @Test
    @DisplayName("获取天气信息 - adcode为空白返回null")
    void testGetWeatherInfo_BlankAdcode() {
        JsonNode result = amapService.getWeatherInfo("   ", "base");
        assertNull(result);
    }

    @Test
    @DisplayName("POI关键字搜索 - 成功搜索")
    void testSearchPoisByKeyword_Success() throws Exception {
        String mockResponse = "{\"status\":\"1\",\"pois\":[{\"name\":\"故宫\",\"location\":\"116.397428,39.90923\"}]}";
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(mockResponse);

        JsonNode result = amapService.searchPoisByKeyword("故宫", null, "北京", 1);

        assertNotNull(result);
        assertTrue(result.isArray());
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("POI关键字搜索 - 关键字为空返回null")
    void testSearchPoisByKeyword_NullKeywords() {
        JsonNode result = amapService.searchPoisByKeyword(null, null, null, 1);
        assertNull(result);
    }

    @Test
    @DisplayName("POI关键字搜索 - 关键字为空白返回null")
    void testSearchPoisByKeyword_BlankKeywords() {
        JsonNode result = amapService.searchPoisByKeyword("   ", null, null, 1);
        assertNull(result);
    }

    @Test
    @DisplayName("POI周边搜索 - 成功搜索")
    void testSearchPoisAround_Success() throws Exception {
        String mockResponse = "{\"status\":\"1\",\"pois\":[{\"name\":\"餐厅\",\"location\":\"116.397428,39.90923\"}]}";
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(mockResponse);

        JsonNode result = amapService.searchPoisAround("116.397428,39.90923", "餐厅", null, 3000);

        assertNotNull(result);
        assertTrue(result.isArray());
    }

    @Test
    @DisplayName("POI周边搜索 - 位置为空返回null")
    void testSearchPoisAround_NullLocation() {
        JsonNode result = amapService.searchPoisAround(null, "餐厅", null, 3000);
        assertNull(result);
    }

    @Test
    @DisplayName("POI周边搜索 - 位置为空白返回null")
    void testSearchPoisAround_BlankLocation() {
        JsonNode result = amapService.searchPoisAround("   ", "餐厅", null, 3000);
        assertNull(result);
    }

    @Test
    @DisplayName("生成静态地图URL - 成功生成")
    void testGenerateStaticMapUrl_Success() {
        String result = amapService.generateStaticMapUrl("116.397428,39.90923", 15, null);

        assertNotNull(result);
        assertTrue(result.contains("key=" + testKey));
        assertTrue(result.contains("location=116.397428,39.90923"));
        assertTrue(result.contains("zoom=15"));
    }

    @Test
    @DisplayName("生成静态地图URL - 包含标签")
    void testGenerateStaticMapUrl_WithLabels() {
        String result = amapService.generateStaticMapUrl("116.397428,39.90923", 15, "116.39,39.90,A");

        assertNotNull(result);
        assertTrue(result.contains("markers="));
    }

    @Test
    @DisplayName("生成轨迹地图URL - 成功生成")
    void testGenerateTrajectoryMapUrl_Success() {
        List<Location> trajectory = new ArrayList<>();
        Location loc1 = new Location();
        loc1.setLng(116.397428);
        loc1.setLat(39.90923);
        Location loc2 = new Location();
        loc2.setLng(116.403963);
        loc2.setLat(39.91409);
        trajectory.add(loc1);
        trajectory.add(loc2);

        String result = amapService.generateTrajectoryMapUrl(trajectory);

        assertNotNull(result);
        assertTrue(result.contains("paths="));
        assertTrue(result.contains("markers="));
    }

    @Test
    @DisplayName("生成轨迹地图URL - 轨迹为空返回null")
    void testGenerateTrajectoryMapUrl_EmptyTrajectory() {
        List<Location> trajectory = new ArrayList<>();
        String result = amapService.generateTrajectoryMapUrl(trajectory);
        assertNull(result);
    }

    @Test
    @DisplayName("生成轨迹地图URL - 轨迹为null返回null")
    void testGenerateTrajectoryMapUrl_NullTrajectory() {
        String result = amapService.generateTrajectoryMapUrl(null);
        assertNull(result);
    }

    @Test
    @DisplayName("生成轨迹地图URL - 单点轨迹")
    void testGenerateTrajectoryMapUrl_SinglePoint() {
        List<Location> trajectory = new ArrayList<>();
        Location loc = new Location();
        loc.setLng(116.397428);
        loc.setLat(39.90923);
        trajectory.add(loc);

        String result = amapService.generateTrajectoryMapUrl(trajectory);

        assertNotNull(result);
        assertTrue(result.contains("location="));
    }

    @Test
    @DisplayName("路径规划 - 步行路线")
    void testGetRoutePlanning_Walking() throws Exception {
        String mockResponse = "{\"status\":\"1\",\"route\":{\"paths\":[{\"duration\":\"600\"}]}}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(mockResponse);

        JsonNode result = amapService.getRoutePlanning("116.397428,39.90923", "116.403963,39.91409", "walking", null);

        assertNotNull(result);
        assertTrue(result.has("paths"));
    }

    @Test
    @DisplayName("路径规划 - 驾车路线")
    void testGetRoutePlanning_Driving() throws Exception {
        String mockResponse = "{\"status\":\"1\",\"route\":{\"paths\":[{\"duration\":\"300\"}]}}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(mockResponse);

        JsonNode result = amapService.getRoutePlanning("116.397428,39.90923", "116.403963,39.91409", "driving", null);

        assertNotNull(result);
        assertTrue(result.has("paths"));
    }

    @Test
    @DisplayName("路径规划 - 公交路线")
    void testGetRoutePlanning_Transit() throws Exception {
        String mockResponse = "{\"status\":\"1\",\"route\":{\"transits\":[{\"duration\":\"900\"}]}}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(mockResponse);

        JsonNode result = amapService.getRoutePlanning("116.397428,39.90923", "116.403963,39.91409", "transit", "北京");

        assertNotNull(result);
    }

    @Test
    @DisplayName("路径规划 - 公交路线城市为空返回null")
    void testGetRoutePlanning_TransitNullCity() {
        JsonNode result = amapService.getRoutePlanning("116.397428,39.90923", "116.403963,39.91409", "transit", null);
        assertNull(result);
    }

    @Test
    @DisplayName("路径规划 - 骑行路线")
    void testGetRoutePlanning_Bicycling() throws Exception {
        String mockResponse = "{\"errcode\":0,\"data\":{\"paths\":[{\"duration\":\"450\"}]}}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(mockResponse);

        JsonNode result = amapService.getRoutePlanning("116.397428,39.90923", "116.403963,39.91409", "bicycling", null);

        assertNotNull(result);
        assertTrue(result.has("paths"));
    }

    @Test
    @DisplayName("路径规划 - 无效类型返回null")
    void testGetRoutePlanning_InvalidType() {
        JsonNode result = amapService.getRoutePlanning("116.397428,39.90923", "116.403963,39.91409", "invalid", null);
        assertNull(result);
    }

    @Test
    @DisplayName("估算行程时间 - 成功估算")
    void testEstimateTravelTime_Success() throws Exception {
        String mockResponse = "{\"status\":\"1\",\"route\":{\"paths\":[{\"duration\":\"600\"}]}}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(mockResponse);

        Integer result = amapService.estimateTravelTime("116.397428,39.90923", "116.403963,39.91409", "walking", null);

        assertNotNull(result);
        assertEquals(600, result);
    }

    @Test
    @DisplayName("获取最快路线 - 成功获取")
    void testGetFastestRoute_Success() throws Exception {
        String mockResponse = "{\"status\":\"1\",\"route\":{\"paths\":[{\"duration\":\"600\"}]}}";
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn(mockResponse);

        JsonNode result = amapService.getFastestRoute("116.397428,39.90923", "116.403963,39.91409", null);

        assertNotNull(result);
        assertTrue(result.has("mode"));
        assertTrue(result.has("duration"));
    }

    @Test
    @DisplayName("API异常处理 - 返回null")
    void testApiException_ReturnsNull() throws Exception {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenThrow(new RuntimeException("API Error"));

        JsonNode result = amapService.getWeatherInfo("110000", "base");

        assertNull(result);
    }

    @Test
    @DisplayName("景点关键词识别 - 景区关键词走POI搜索")
    void testIsScenicKeyword_ScenicWord() throws Exception {
        String mockResponse = "{\"status\":\"1\",\"pois\":[{\"location\":\"116.397428,39.90923\",\"name\":\"故宫\",\"pname\":\"北京市\",\"cityname\":\"北京市\",\"adname\":\"东城区\"}]}";
        when(restTemplate.getForObject(any(URI.class), eq(String.class))).thenReturn(mockResponse);

        JsonNode result = amapService.getGeocodeInfo("故宫景区");

        assertNotNull(result);
    }
}
