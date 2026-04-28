package com.travel.intellitravel_pioneer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.intellitravel_pioneer.IntellitravelPioneerApplication;
import com.travel.intellitravel_pioneer.po.UserPO;
import com.travel.intellitravel_pioneer.repository.UserRepository;
import com.travel.intellitravel_pioneer.vo.user.UserVO;
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
class UserControllerFunctionalTest {

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private static final String TEST_USERNAME = "functional_test_user";
    private static final String TEST_PASSWORD = "testPassword123";
    private static final String TEST_EMAIL = "functional@test.com";

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    @Order(1)
    @DisplayName("功能测试 - 用户注册流程")
    void testUserRegistrationFlow() throws Exception {
        UserVO userVO = new UserVO();
        userVO.setUsername(TEST_USERNAME);
        userVO.setPassword(TEST_PASSWORD);
        userVO.setEmail(TEST_EMAIL);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        HttpEntity<UserVO> request = new HttpEntity<>(userVO, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url("/api/user"), request, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Success"));

        UserPO savedUser = userRepository.findByUsername(TEST_USERNAME);
        assertNotNull(savedUser);
        assertEquals(TEST_USERNAME, savedUser.getUsername());
        assertEquals(TEST_EMAIL, savedUser.getEmail());
    }

    @Test
    @Order(2)
    @DisplayName("功能测试 - 用户登录流程")
    void testUserLoginFlow() throws Exception {
        UserPO user = new UserPO();
        user.setUsername(TEST_USERNAME + "_login");
        user.setPassword(TEST_PASSWORD);
        user.setEmail("login@test.com");
        userRepository.save(user);

        UserVO loginVO = new UserVO();
        loginVO.setUsername(TEST_USERNAME + "_login");
        loginVO.setPassword(TEST_PASSWORD);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        HttpEntity<UserVO> request = new HttpEntity<>(loginVO, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url("/api/user/login"), request, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("data"));
    }

    @Test
    @Order(3)
    @DisplayName("功能测试 - 登录失败 - 密码错误")
    void testUserLoginFailure_WrongPassword() throws Exception {
        UserPO user = new UserPO();
        user.setUsername(TEST_USERNAME + "_wrong_pwd");
        user.setPassword(TEST_PASSWORD);
        user.setEmail("wrongpwd@test.com");
        userRepository.save(user);

        UserVO loginVO = new UserVO();
        loginVO.setUsername(TEST_USERNAME + "_wrong_pwd");
        loginVO.setPassword("wrongPassword");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        HttpEntity<UserVO> request = new HttpEntity<>(loginVO, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url("/api/user/login"), request, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("500"));
    }

    @Test
    @Order(4)
    @DisplayName("功能测试 - 获取用户信息流程")
    void testGetUserInfoFlow() throws Exception {
        UserPO user = new UserPO();
        user.setUsername(TEST_USERNAME + "_info");
        user.setPassword(TEST_PASSWORD);
        user.setEmail("info@test.com");
        user.setName("Test User Info");
        user.setExperience(100);
        userRepository.save(user);

        ResponseEntity<String> response = restTemplate.getForEntity(url("/api/user/" + TEST_USERNAME + "_info"),
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains(TEST_USERNAME + "_info"));
        assertTrue(response.getBody().contains("info@test.com"));
    }

    @Test
    @Order(5)
    @DisplayName("功能测试 - 获取不存在用户信息")
    void testGetUserInfo_NotFound() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/api/user/nonexistent_user"), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("500"));
    }

    @Test
    @Order(6)
    @DisplayName("功能测试 - 更新用户信息流程")
    void testUpdateUserInfoFlow() throws Exception {
        UserPO user = new UserPO();
        user.setUsername(TEST_USERNAME + "_update");
        user.setPassword(TEST_PASSWORD);
        user.setEmail("update@test.com");
        userRepository.save(user);

        UserVO updateVO = new UserVO();
        updateVO.setUsername(TEST_USERNAME + "_update");
        updateVO.setName("Updated Name");
        updateVO.setEmail("updated@test.com");
        updateVO.setTelephone("13800138000");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        HttpEntity<UserVO> request = new HttpEntity<>(updateVO, headers);

        ResponseEntity<String> response = restTemplate.exchange(url("/api/user"), HttpMethod.PUT, request,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("Success"));

        UserPO updatedUser = userRepository.findByUsername(TEST_USERNAME + "_update");
        assertNotNull(updatedUser);
        assertEquals("Updated Name", updatedUser.getName());
        assertEquals("updated@test.com", updatedUser.getEmail());
        assertEquals("13800138000", updatedUser.getTelephone());
    }

    @Test
    @Order(7)
    @DisplayName("功能测试 - 增加用户经验值流程")
    void testAddExperienceFlow() throws Exception {
        UserPO user = new UserPO();
        user.setUsername(TEST_USERNAME + "_exp");
        user.setPassword(TEST_PASSWORD);
        user.setEmail("exp@test.com");
        user.setExperience(100);
        userRepository.save(user);

        UserVO expVO = new UserVO();
        expVO.setExperience(50);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        HttpEntity<UserVO> request = new HttpEntity<>(expVO, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/api/user/" + TEST_USERNAME + "_exp" + "/experience"), request, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("150"));

        UserPO updatedUser = userRepository.findByUsername(TEST_USERNAME + "_exp");
        assertNotNull(updatedUser);
        assertEquals(150, updatedUser.getExperience());
    }

    @Test
    @Order(8)
    @DisplayName("功能测试 - 完整用户流程：注册->登录->获取信息->更新")
    void testCompleteUserFlow() throws Exception {
        String flowUsername = "complete_flow_user";

        UserVO registerVO = new UserVO();
        registerVO.setUsername(flowUsername);
        registerVO.setPassword(TEST_PASSWORD);
        registerVO.setEmail("flow@test.com");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        HttpEntity<UserVO> registerRequest = new HttpEntity<>(registerVO, headers);

        ResponseEntity<String> registerResponse = restTemplate.postForEntity(url("/api/user"), registerRequest,
                String.class);
        assertEquals(HttpStatus.OK, registerResponse.getStatusCode());

        UserVO loginVO = new UserVO();
        loginVO.setUsername(flowUsername);
        loginVO.setPassword(TEST_PASSWORD);
        HttpEntity<UserVO> loginRequest = new HttpEntity<>(loginVO, headers);

        ResponseEntity<String> loginResponse = restTemplate.postForEntity(url("/api/user/login"), loginRequest,
                String.class);
        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());

        ResponseEntity<String> infoResponse = restTemplate.getForEntity(url("/api/user/" + flowUsername), String.class);
        assertEquals(HttpStatus.OK, infoResponse.getStatusCode());
        assertTrue(infoResponse.getBody().contains(flowUsername));

        UserVO updateVO = new UserVO();
        updateVO.setUsername(flowUsername);
        updateVO.setName("Complete Flow User");
        updateVO.setEmail("complete@test.com");
        HttpEntity<UserVO> updateRequest = new HttpEntity<>(updateVO, headers);

        ResponseEntity<String> updateResponse = restTemplate.exchange(url("/api/user"), HttpMethod.PUT, updateRequest,
                String.class);
        assertEquals(HttpStatus.OK, updateResponse.getStatusCode());

        ResponseEntity<String> finalInfoResponse = restTemplate.getForEntity(url("/api/user/" + flowUsername),
                String.class);
        assertTrue(finalInfoResponse.getBody().contains("Complete Flow User"));
        assertTrue(finalInfoResponse.getBody().contains("complete@test.com"));
    }

    @Test
    @Order(9)
    @DisplayName("功能测试 - 重复注册失败")
    void testDuplicateRegistration() throws Exception {
        UserPO existingUser = new UserPO();
        existingUser.setUsername("duplicate_user");
        existingUser.setPassword(TEST_PASSWORD);
        existingUser.setEmail("duplicate@test.com");
        userRepository.save(existingUser);

        UserVO duplicateVO = new UserVO();
        duplicateVO.setUsername("duplicate_user");
        duplicateVO.setPassword("newPassword");
        duplicateVO.setEmail("new@test.com");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        HttpEntity<UserVO> request = new HttpEntity<>(duplicateVO, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url("/api/user"), request, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("500"));
    }

    @Test
    @Order(10)
    @DisplayName("功能测试 - 更新不存在用户信息")
    void testUpdateNonExistentUser() throws Exception {
        UserVO updateVO = new UserVO();
        updateVO.setUsername("nonexistent_update_user");
        updateVO.setName("New Name");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        HttpEntity<UserVO> request = new HttpEntity<>(updateVO, headers);

        ResponseEntity<String> response = restTemplate.exchange(url("/api/user"), HttpMethod.PUT, request,
                String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("500"));
    }

    @Test
    @Order(11)
    @DisplayName("功能测试 - 增加经验值给不存在用户")
    void testAddExperienceToNonExistentUser() throws Exception {
        UserVO expVO = new UserVO();
        expVO.setExperience(50);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        HttpEntity<UserVO> request = new HttpEntity<>(expVO, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                url("/api/user/nonexistent_exp_user/experience"), request, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("500"));
    }

    @Test
    @Order(12)
    @DisplayName("功能测试 - 空用户名注册")
    void testRegisterWithEmptyUsername() throws Exception {
        UserVO userVO = new UserVO();
        userVO.setUsername("");
        userVO.setPassword(TEST_PASSWORD);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        HttpEntity<UserVO> request = new HttpEntity<>(userVO, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url("/api/user"), request, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

@Test
    @Order(13)
    @DisplayName("功能测试 - 空密码登录")
    void testLoginWithEmptyPassword() throws Exception {
        UserPO user = new UserPO();
        user.setUsername("empty_pwd_user");
        user.setPassword(TEST_PASSWORD);
        user.setEmail("emptypwd@test.com");
        userRepository.save(user);

        UserVO loginVO = new UserVO();
        loginVO.setUsername("empty_pwd_user");
        loginVO.setPassword("");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        HttpEntity<UserVO> request = new HttpEntity<>(loginVO, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url("/api/user/login"), request, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("500"));
    }
}
