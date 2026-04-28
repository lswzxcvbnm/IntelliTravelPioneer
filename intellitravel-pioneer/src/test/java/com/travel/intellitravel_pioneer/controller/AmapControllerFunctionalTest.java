package com.travel.intellitravel_pioneer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.intellitravel_pioneer.IntellitravelPioneerApplication;
import com.travel.intellitravel_pioneer.po.UserPO;
import com.travel.intellitravel_pioneer.repository.UserRepository;
import com.travel.intellitravel_pioneer.util.TokenUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = IntellitravelPioneerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional
class AmapControllerFunctionalTest {

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TokenUtil tokenUtil;

    private static final String TEST_USERNAME = "amap_test_user";
    private static final String TEST_PASSWORD = "testPassword123";
    private static final String TEST_EMAIL = "amap@test.com";

    private UserPO testUser;
    private String testToken;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        restTemplate.setUriTemplateHandler(new DefaultUriBuilderFactory("http://localhost:" + port));

        testUser = new UserPO();
        testUser.setUsername(TEST_USERNAME);
        testUser.setPassword(TEST_PASSWORD);
        testUser.setEmail(TEST_EMAIL);
        testUser = userRepository.save(testUser);

        testToken = resolveTestToken(testUser);
    }

    private String resolveTestToken(UserPO user) {
        String[] methodNames = { "getToken", "createToken", "generateToken", "createJwtToken", "generateJwtToken" };

        for (String methodName : methodNames) {
            try {
                Method m = tokenUtil.getClass().getMethod(methodName, UserPO.class);
                Object token = m.invoke(tokenUtil, user);
                if (token != null) {
                    return token.toString();
                }
            } catch (Exception ignored) {
            }

            try {
                Method m = tokenUtil.getClass().getMethod(methodName, String.class);
                Object token = m.invoke(tokenUtil, user.getUsername());
                if (token != null) {
                    return token.toString();
                }
            } catch (Exception ignored) {
            }

            try {
                Method m = tokenUtil.getClass().getMethod(methodName, Long.class);
                Object token = m.invoke(tokenUtil, user.getId());
                if (token != null) {
                    return token.toString();
                }
            } catch (Exception ignored) {
            }

            try {
                Method m = tokenUtil.getClass().getMethod(methodName, long.class);
                Object token = m.invoke(tokenUtil, user.getId() == null ? 0L : user.getId());
                if (token != null) {
                    return token.toString();
                }
            } catch (Exception ignored) {
            }
        }

        throw new IllegalStateException("No compatible token generation method found in TokenUtil.");
    }

    private Map<String, Object> createLocation(double latitude, double longitude) {
        Map<String, Object> location = new LinkedHashMap<>();
        location.put("latitude", latitude);
        location.put("longitude", longitude);
        location.put("lat", latitude);
        location.put("lng", longitude);
        return location;
    }

    @Test
    @Order(1)
    @DisplayName("功能测试 - 获取天气信息")
    void testGetWeather() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", testToken);
        HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/amap/weather?city=110000&extensions=base",
                HttpMethod.GET,
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @Order(2)
    @DisplayName("功能测试 - 获取天气信息 - 无效Token")
    void testGetWeather_InvalidToken() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", "invalid_token");
        HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/amap/weather?city=110000&extensions=base",
                HttpMethod.GET,
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @Order(3)
    @DisplayName("功能测试 - POI关键字搜索")
    void testSearchPoiText() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", testToken);
        HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/amap/poi/text?keywords=故宫&city=北京",
                HttpMethod.GET,
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @Order(4)
    @DisplayName("功能测试 - POI关键字搜索 - 无关键字")
    void testSearchPoiText_NoKeywords() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", testToken);
        HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/amap/poi/text?keywords=",
                HttpMethod.GET,
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @Order(5)
    @DisplayName("功能测试 - POI周边搜索")
    void testSearchPoiAround() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", testToken);
        HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/amap/poi/around?location=116.397428,39.90923&keywords=餐厅&radius=1000",
                HttpMethod.GET,
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @Order(6)
    @DisplayName("功能测试 - POI周边搜索 - 无位置")
    void testSearchPoiAround_NoLocation() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", testToken);
        HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/amap/poi/around?location=",
                HttpMethod.GET,
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @Order(7)
    @DisplayName("功能测试 - 地理编码")
    void testGetGeocode() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", testToken);
        HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/amap/geocode?address=北京市朝阳区阜通东大街6号",
                HttpMethod.GET,
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @Order(8)
    @DisplayName("功能测试 - 地理编码 - 指定城市")
    void testGetGeocode_WithCity() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", testToken);
        HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/amap/geocode?address=故宫&city=北京",
                HttpMethod.GET,
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @Order(9)
    @DisplayName("功能测试 - 地理编码 - 无效Token")
    void testGetGeocode_InvalidToken() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", "invalid_token");
        HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/amap/geocode?address=北京市朝阳区",
                HttpMethod.GET,
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("500"));
    }

    @Test
    @Order(10)
    @DisplayName("功能测试 - 获取静态地图图片")
    void testGetStaticMapImage() throws Exception {
        ResponseEntity<byte[]> response = restTemplate.exchange(
                "/api/amap/static-map/image?location=116.397428,39.90923&zoom=15&token=" + testToken,
                HttpMethod.GET,
                null,
                byte[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @Order(11)
    @DisplayName("功能测试 - 获取静态地图图片 - 无Token")
    void testGetStaticMapImage_NoToken() throws Exception {
        ResponseEntity<byte[]> response = restTemplate.exchange(
                "/api/amap/static-map/image?location=116.397428,39.90923&zoom=15",
                HttpMethod.GET,
                null,
                byte[].class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @Order(12)
    @DisplayName("功能测试 - 路径规划 - 步行")
    void testGetRoute_Walking() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", testToken);
        HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/amap/route?origin=116.397428,39.90923&destination=116.403963,39.91409&type=walking",
                HttpMethod.GET,
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @Order(13)
    @DisplayName("功能测试 - 路径规划 - 驾车")
    void testGetRoute_Driving() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", testToken);
        HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/amap/route?origin=116.397428,39.90923&destination=116.403963,39.91409&type=driving",
                HttpMethod.GET,
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @Order(14)
    @DisplayName("功能测试 - 路径规划 - 公交")
    void testGetRoute_Transit() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", testToken);
        HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/amap/route?origin=116.397428,39.90923&destination=116.403963,39.91409&type=transit&city=北京",
                HttpMethod.GET,
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @Order(15)
    @DisplayName("功能测试 - 路径规划 - 无效Token")
    void testGetRoute_InvalidToken() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", "invalid_token");
        HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/amap/route?origin=116.397428,39.90923&destination=116.403963,39.91409&type=walking",
                HttpMethod.GET,
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("500"));
    }

    @Test
    @Order(16)
    @DisplayName("功能测试 - 获取最快路线")
    void testGetFastestRoute() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", testToken);
        HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/amap/route/fastest?origin=116.397428,39.90923&destination=116.403963,39.91409",
                HttpMethod.GET,
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @Order(17)
    @DisplayName("功能测试 - 获取最快路线 - 无效Token")
    void testGetFastestRoute_InvalidToken() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("token", "invalid_token");
        HttpEntity<Void> httpRequest = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/amap/route/fastest?origin=116.397428,39.90923&destination=116.403963,39.91409",
                HttpMethod.GET,
                httpRequest,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("500"));
    }

    @Test
    @Order(18)
    @DisplayName("功能测试 - 生成轨迹地图")
    void testGetTrajectoryStaticMap() throws Exception {
        List<Map<String, Object>> trajectory = Arrays.asList(
                createLocation(39.897445, 116.390291),
                createLocation(39.90923, 116.397428),
                createLocation(39.91409, 116.403963));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set("token", testToken);
        HttpEntity<List<Map<String, Object>>> httpRequest = new HttpEntity<>(trajectory, headers);

        ResponseEntity<byte[]> response = restTemplate.exchange(
                "/api/amap/static-map/trajectory",
                HttpMethod.POST,
                httpRequest,
                byte[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @Order(19)
    @DisplayName("功能测试 - 生成轨迹地图 - 无Token")
    void testGetTrajectoryStaticMap_NoToken() throws Exception {
        List<Map<String, Object>> trajectory = Arrays.asList(
                createLocation(39.897445, 116.390291),
                createLocation(39.90923, 116.397428));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        HttpEntity<List<Map<String, Object>>> httpRequest = new HttpEntity<>(trajectory, headers);

        ResponseEntity<byte[]> response = restTemplate.exchange(
                "/api/amap/static-map/trajectory",
                HttpMethod.POST,
                httpRequest,
                byte[].class);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @Order(20)
    @DisplayName("功能测试 - 生成轨迹地图 - 空轨迹")
    void testGetTrajectoryStaticMap_EmptyTrajectory() throws Exception {
        List<Map<String, Object>> trajectory = Arrays.asList();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        headers.set("token", testToken);
        HttpEntity<List<Map<String, Object>>> httpRequest = new HttpEntity<>(trajectory, headers);

        ResponseEntity<byte[]> response = restTemplate.exchange(
                "/api/amap/static-map/trajectory",
                HttpMethod.POST,
                httpRequest,
                byte[].class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
