package com.travel.intellitravel_pioneer.service.serviceImpl;

import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import com.travel.intellitravel_pioneer.po.UserPO;
import com.travel.intellitravel_pioneer.repository.UserRepository;
import com.travel.intellitravel_pioneer.util.TokenUtil;
import com.travel.intellitravel_pioneer.vo.user.UserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenUtil tokenUtil;

    @InjectMocks
    private UserServiceImpl userService;

    private UserPO testUserPO;
    private UserVO testUserVO;

    @BeforeEach
    void setUp() {
        testUserPO = new UserPO();
        testUserPO.setId(1);
        testUserPO.setUsername("testuser");
        testUserPO.setPassword("password123");
        testUserPO.setName("Test User");
        testUserPO.setGrade("1");
        testUserPO.setExperience(0);
        testUserPO.setEmail("test@example.com");
        testUserPO.setTelephone("13800138000");
        testUserPO.setLocation("Beijing");

        testUserVO = new UserVO();
        testUserVO.setUsername("testuser");
        testUserVO.setPassword("password123");
        testUserVO.setName("Test User");
        testUserVO.setEmail("test@example.com");
        testUserVO.setTelephone("13800138000");
        testUserVO.setLocation("Beijing");
    }

    @Test
    @DisplayName("用户注册 - 成功注册新用户")
    void testRegister_Success() {
        when(userRepository.findByUsername("testuser")).thenReturn(null);
        when(userRepository.save(any(UserPO.class))).thenReturn(testUserPO);

        String result = userService.register(testUserVO, "password123");

        assertEquals("Success", result);
        verify(userRepository, times(1)).save(any(UserPO.class));
    }

    @Test
    @DisplayName("用户注册 - 用户名已存在抛出异常")
    void testRegister_UserAlreadyExists() {
        when(userRepository.findByUsername("testuser")).thenReturn(testUserPO);

        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> userService.register(testUserVO, "password123"));

        assertEquals("User already exists", exception.getMessage());
        verify(userRepository, never()).save(any(UserPO.class));
    }

    @Test
    @DisplayName("用户登录 - 成功登录")
    void testLogin_Success() {
        when(userRepository.findByUsername("testuser")).thenReturn(testUserPO);
        when(tokenUtil.getToken(any(UserPO.class))).thenReturn("mock-token");

        String result = userService.login("testuser", "password123");

        assertEquals("mock-token", result);
    }

    @Test
    @DisplayName("用户登录 - 用户不存在抛出异常")
    void testLogin_UserNotFound() {
        when(userRepository.findByUsername("nonexistent")).thenReturn(null);

        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> userService.login("nonexistent", "password123"));

        assertEquals("Incorrect username or password", exception.getMessage());
    }

    @Test
    @DisplayName("用户登录 - 密码错误抛出异常")
    void testLogin_WrongPassword() {
        when(userRepository.findByUsername("testuser")).thenReturn(testUserPO);

        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> userService.login("testuser", "wrongpassword"));

        assertEquals("Incorrect username or password", exception.getMessage());
    }

    @Test
    @DisplayName("获取用户信息 - 成功获取")
    void testGetInformation_Success() {
        when(userRepository.findByUsername("testuser")).thenReturn(testUserPO);

        UserVO result = userService.getInformation("testuser");

        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
        assertEquals("Test User", result.getName());
    }

    @Test
    @DisplayName("获取用户信息 - 用户不存在抛出异常")
    void testGetInformation_UserNotFound() {
        when(userRepository.findByUsername("nonexistent")).thenReturn(null);

        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> userService.getInformation("nonexistent"));

        assertEquals("User not found", exception.getMessage());
    }

    @Test
    @DisplayName("更新用户信息 - 成功更新")
    void testUpdateInformation_Success() {
        UserVO updateVO = new UserVO();
        updateVO.setUsername("testuser");
        updateVO.setName("Updated Name");
        updateVO.setEmail("updated@example.com");

        when(userRepository.findByUsername("testuser")).thenReturn(testUserPO);
        when(userRepository.save(any(UserPO.class))).thenReturn(testUserPO);

        String result = userService.updateInformation(updateVO);

        assertEquals("Success", result);
        verify(userRepository, times(1)).save(any(UserPO.class));
    }

    @Test
    @DisplayName("更新用户信息 - 用户不存在抛出异常")
    void testUpdateInformation_UserNotFound() {
        UserVO updateVO = new UserVO();
        updateVO.setUsername("nonexistent");

        when(userRepository.findByUsername("nonexistent")).thenReturn(null);

        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> userService.updateInformation(updateVO));

        assertEquals("User not found", exception.getMessage());
    }

    @Test
    @DisplayName("增加经验值 - 成功增加经验值")
    void testAddExperience_Success() {
        testUserPO.setGrade("1");
        testUserPO.setExperience(0);

        when(userRepository.findByUsername("testuser")).thenReturn(testUserPO);
        when(userRepository.save(any(UserPO.class))).thenReturn(testUserPO);

        UserVO result = userService.addExperience("testuser", 50);

        assertNotNull(result);
        verify(userRepository, times(1)).save(any(UserPO.class));
    }

    @Test
    @DisplayName("增加经验值 - 经验值为负数抛出异常")
    void testAddExperience_NegativeExperience() {
        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> userService.addExperience("testuser", -10));

        assertEquals("experience must be positive", exception.getMessage());
    }

    @Test
    @DisplayName("增加经验值 - 经验值为0抛出异常")
    void testAddExperience_ZeroExperience() {
        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> userService.addExperience("testuser", 0));

        assertEquals("experience must be positive", exception.getMessage());
    }

    @Test
    @DisplayName("增加经验值 - 用户不存在抛出异常")
    void testAddExperience_UserNotFound() {
        when(userRepository.findByUsername("nonexistent")).thenReturn(null);

        IntellitravelException exception = assertThrows(
                IntellitravelException.class,
                () -> userService.addExperience("nonexistent", 100));

        assertEquals("User not found", exception.getMessage());
    }
}
