package com.travel.intellitravel_pioneer.service.serviceImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import com.travel.intellitravel_pioneer.po.CapsuleMediaPO;
import com.travel.intellitravel_pioneer.po.CapsulePO;
import com.travel.intellitravel_pioneer.po.UserPO;
import com.travel.intellitravel_pioneer.repository.CapsuleMediaRepository;
import com.travel.intellitravel_pioneer.repository.CapsuleRepository;
import com.travel.intellitravel_pioneer.repository.UserRepository;
import com.travel.intellitravel_pioneer.service.AmapService;
import com.travel.intellitravel_pioneer.vo.capsule.CapsuleDetailVO;
import com.travel.intellitravel_pioneer.vo.capsule.CapsuleListItemVO;
import com.travel.intellitravel_pioneer.vo.capsule.LocationVO;
import com.travel.intellitravel_pioneer.vo.capsule.MediaFileVO;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CapsuleServiceImplTest {

    @Mock
    private CapsuleRepository capsuleRepository;

    @Mock
    private CapsuleMediaRepository capsuleMediaRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AmapService amapService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private CapsuleServiceImpl capsuleService;

    private CapsulePO testCapsule;
    private UserPO testUser;
    private CapsuleDetailVO testCapsuleDetail;

    @BeforeEach
    void setUp() {
        testUser = new UserPO();
        testUser.setId(1);
        testUser.setUsername("testuser");

        testCapsule = CapsulePO.builder()
                .id(1L)
                .userId(1L)
                .title("Test Capsule")
                .content("Test Content")
                .decoration("star")
                .color("#FF5733")
                .isPublic(true)
                .capsuleDate("2024-01-01")
                .locationName("北京市朝阳区")
                .address("北京市朝阳区阜通东大街6号")
                .latitude(39.90923)
                .longitude(116.397428)
                .city("北京市")
                .adcode("110105")
                .likesCount(0)
                .commentsCount(0)
                .viewCount(0)
                .createdAt(LocalDateTime.now())
                .build();

        testCapsuleDetail = CapsuleDetailVO.builder()
                .title("Test Capsule")
                .content("Test Content")
                .decoration("star")
                .color("#FF5733")
                .isPublic(true)
                .date("2024-01-01")
                .build();

        lenient().when(userRepository.findById(anyInt())).thenReturn(Optional.of(testUser));
    }

    @Test
    @DisplayName("创建时间胶囊 - 成功创建")
    void testCreateCapsule_Success() {
        when(capsuleRepository.save(any(CapsulePO.class))).thenReturn(testCapsule);

        String result = capsuleService.createCapsule(1L, testCapsuleDetail);

        assertNotNull(result);
        assertEquals("1", result);
        verify(capsuleRepository, times(1)).save(any(CapsulePO.class));
    }

    @Test
    @DisplayName("创建时间胶囊 - 包含位置信息")
    void testCreateCapsule_WithLocation() {
        LocationVO location = LocationVO.builder()
                .name("故宫")
                .address("北京市东城区景山前街4号")
                .city("北京市")
                .build();
        testCapsuleDetail.setLocation(location);

        ObjectNode geocodeNode = objectMapper.createObjectNode();
        geocodeNode.put("formatted_address", "北京市东城区景山前街4号");
        geocodeNode.put("city", "北京市");
        geocodeNode.put("adcode", "110101");
        geocodeNode.put("location", "116.397428,39.918058");
        ArrayNode geocodeArray = objectMapper.createArrayNode();
        geocodeArray.add(geocodeNode);

        when(amapService.getGeocodeInfo(anyString(), any())).thenReturn(geocodeArray);
        when(capsuleRepository.save(any(CapsulePO.class))).thenReturn(testCapsule);

        String result = capsuleService.createCapsule(1L, testCapsuleDetail);

        assertNotNull(result);
        verify(amapService, times(1)).getGeocodeInfo(anyString(), any());
    }

    @Test
    @DisplayName("创建时间胶囊 - 包含图片")
    void testCreateCapsule_WithImages() {
        List<MediaFileVO> images = new ArrayList<>();
        images.add(MediaFileVO.builder()
                .url("https://example.com/image.jpg")
                .type("image")
                .thumbnail("https://example.com/thumb.jpg")
                .width(800)
                .height(600)
                .build());
        testCapsuleDetail.setImages(images);

        when(capsuleRepository.save(any(CapsulePO.class))).thenReturn(testCapsule);
        when(capsuleMediaRepository.saveAll(anyList())).thenReturn(new ArrayList<>());

        String result = capsuleService.createCapsule(1L, testCapsuleDetail);

        assertNotNull(result);
        verify(capsuleMediaRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("删除时间胶囊 - 成功删除")
    void testDeleteCapsule_Success() {
        when(capsuleRepository.findById(1L)).thenReturn(Optional.of(testCapsule));
        doNothing().when(capsuleMediaRepository).deleteByCapsuleId(anyLong());
        doNothing().when(capsuleRepository).delete(any(CapsulePO.class));

        assertDoesNotThrow(() -> capsuleService.deleteCapsule(1L, 1L));

        verify(capsuleMediaRepository, times(1)).deleteByCapsuleId(1L);
        verify(capsuleRepository, times(1)).delete(testCapsule);
    }

    @Test
    @DisplayName("删除时间胶囊 - 胶囊不存在抛出异常")
    void testDeleteCapsule_NotFound() {
        when(capsuleRepository.findById(1L)).thenReturn(Optional.empty());

        IntellitravelException exception = assertThrows(IntellitravelException.class, 
                () -> capsuleService.deleteCapsule(1L, 1L));

        assertEquals("Capsule not found", exception.getMessage());
    }

    @Test
    @DisplayName("删除时间胶囊 - 无权限删除抛出异常")
    void testDeleteCapsule_NotAuthorized() {
        when(capsuleRepository.findById(1L)).thenReturn(Optional.of(testCapsule));

        IntellitravelException exception = assertThrows(IntellitravelException.class, 
                () -> capsuleService.deleteCapsule(1L, 2L));

        assertEquals("Not authorized to delete this capsule", exception.getMessage());
    }

    @Test
    @DisplayName("更新时间胶囊 - 成功更新")
    void testUpdateCapsule_Success() {
        CapsuleDetailVO updateDetail = CapsuleDetailVO.builder()
                .title("Updated Title")
                .content("Updated Content")
                .build();

        when(capsuleRepository.findById(1L)).thenReturn(Optional.of(testCapsule));
        when(capsuleRepository.save(any(CapsulePO.class))).thenReturn(testCapsule);

        assertDoesNotThrow(() -> capsuleService.updateCapsule(1L, 1L, updateDetail));

        verify(capsuleRepository, times(1)).save(any(CapsulePO.class));
    }

    @Test
    @DisplayName("更新时间胶囊 - 胶囊不存在抛出异常")
    void testUpdateCapsule_NotFound() {
        when(capsuleRepository.findById(1L)).thenReturn(Optional.empty());

        IntellitravelException exception = assertThrows(IntellitravelException.class, 
                () -> capsuleService.updateCapsule(1L, 1L, testCapsuleDetail));

        assertEquals("Capsule not found", exception.getMessage());
    }

    @Test
    @DisplayName("更新时间胶囊 - 无权限更新抛出异常")
    void testUpdateCapsule_NotAuthorized() {
        when(capsuleRepository.findById(1L)).thenReturn(Optional.of(testCapsule));

        IntellitravelException exception = assertThrows(IntellitravelException.class, 
                () -> capsuleService.updateCapsule(1L, 2L, testCapsuleDetail));

        assertEquals("Not authorized to update this capsule", exception.getMessage());
    }

    @Test
    @DisplayName("更新时间胶囊 - 包含图片更新")
    void testUpdateCapsule_WithImages() {
        List<MediaFileVO> images = new ArrayList<>();
        images.add(MediaFileVO.builder()
                .url("https://example.com/new-image.jpg")
                .type("image")
                .build());
        testCapsuleDetail.setImages(images);

        when(capsuleRepository.findById(1L)).thenReturn(Optional.of(testCapsule));
        when(capsuleRepository.save(any(CapsulePO.class))).thenReturn(testCapsule);
        doNothing().when(capsuleMediaRepository).deleteByCapsuleId(anyLong());
        when(capsuleMediaRepository.saveAll(anyList())).thenReturn(new ArrayList<>());

        assertDoesNotThrow(() -> capsuleService.updateCapsule(1L, 1L, testCapsuleDetail));

        verify(capsuleMediaRepository, times(1)).deleteByCapsuleId(1L);
        verify(capsuleMediaRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("获取用户胶囊列表 - 成功获取")
    void testGetUserCapsules_Success() {
        List<CapsulePO> capsules = Arrays.asList(testCapsule);
        when(capsuleRepository.findByUserId(1L)).thenReturn(capsules);
        when(capsuleMediaRepository.findByCapsuleId(anyLong())).thenReturn(new ArrayList<>());

        List<CapsuleListItemVO> result = capsuleService.getUserCapsules(1L);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Test Capsule", result.get(0).getTitle());
    }

    @Test
    @DisplayName("获取用户胶囊列表 - 空列表")
    void testGetUserCapsules_EmptyList() {
        when(capsuleRepository.findByUserId(1L)).thenReturn(Collections.emptyList());

        List<CapsuleListItemVO> result = capsuleService.getUserCapsules(1L);

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    @DisplayName("根据地点获取胶囊 - 成功获取")
    void testGetCapsulesByLocation_Success() {
        ObjectNode geocodeNode = objectMapper.createObjectNode();
        geocodeNode.put("formatted_address", "北京市朝阳区");
        geocodeNode.put("city", "北京市");
        geocodeNode.put("adcode", "110105");
        ArrayNode geocodeArray = objectMapper.createArrayNode();
        geocodeArray.add(geocodeNode);

        when(amapService.getGeocodeInfo(anyString(), any())).thenReturn(geocodeArray);
        when(capsuleRepository.findByAdcode(anyString())).thenReturn(Arrays.asList(testCapsule));
        when(capsuleMediaRepository.findByCapsuleId(anyLong())).thenReturn(new ArrayList<>());

        List<CapsuleListItemVO> result = capsuleService.getCapsulesByLocation("北京市朝阳区");

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("根据地点获取胶囊 - 空地点返回空列表")
    void testGetCapsulesByLocation_EmptyLocation() {
        List<CapsuleListItemVO> result = capsuleService.getCapsulesByLocation("");

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    @DisplayName("根据地点获取胶囊 - null地点返回空列表")
    void testGetCapsulesByLocation_NullLocation() {
        List<CapsuleListItemVO> result = capsuleService.getCapsulesByLocation((String) null);

        assertNotNull(result);
        assertEquals(0, result.size());
    }

    @Test
    @DisplayName("根据经纬度获取附近胶囊 - 成功获取")
    void testGetCapsulesByLocationNearby_Success() {
        when(capsuleRepository.findByLatitudeBetweenAndLongitudeBetween(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Arrays.asList(testCapsule));
        when(capsuleMediaRepository.findByCapsuleId(anyLong())).thenReturn(new ArrayList<>());

        List<CapsuleListItemVO> result = capsuleService.getCapsulesByLocation(39.90923, 116.397428, 1000.0);

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("根据经纬度获取附近胶囊 - 无效参数抛出异常")
    void testGetCapsulesByLocationNearby_InvalidParams() {
        assertThrows(IllegalArgumentException.class, 
                () -> capsuleService.getCapsulesByLocation(null, 116.397428, 1000.0));
        
        assertThrows(IllegalArgumentException.class, 
                () -> capsuleService.getCapsulesByLocation(39.90923, null, 1000.0));
        
        assertThrows(IllegalArgumentException.class, 
                () -> capsuleService.getCapsulesByLocation(39.90923, 116.397428, -1.0));
    }

    @Test
    @DisplayName("判断同一地点是否存在胶囊 - 存在返回true")
    void testIsCapsuleAtSameLocation_Exists() {
        when(capsuleRepository.findByLatitudeBetweenAndLongitudeBetween(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Arrays.asList(testCapsule));

        boolean result = capsuleService.isCapsuleAtSameLocation(1L, 39.90923, 116.397428, 100.0);

        assertTrue(result);
    }

    @Test
    @DisplayName("判断同一地点是否存在胶囊 - 不存在返回false")
    void testIsCapsuleAtSameLocation_NotExists() {
        when(capsuleRepository.findByLatitudeBetweenAndLongitudeBetween(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Collections.emptyList());

        boolean result = capsuleService.isCapsuleAtSameLocation(1L, 39.90923, 116.397428, 100.0);

        assertFalse(result);
    }

    @Test
    @DisplayName("判断同一地点是否存在胶囊 - 参数为空返回false")
    void testIsCapsuleAtSameLocation_NullParams() {
        assertFalse(capsuleService.isCapsuleAtSameLocation(null, 39.90923, 116.397428, 100.0));
        assertFalse(capsuleService.isCapsuleAtSameLocation(1L, null, 116.397428, 100.0));
        assertFalse(capsuleService.isCapsuleAtSameLocation(1L, 39.90923, null, 100.0));
    }

    @Test
    @DisplayName("判断同一地点是否存在胶囊 - 不同用户返回false")
    void testIsCapsuleAtSameLocation_DifferentUser() {
        CapsulePO otherUserCapsule = CapsulePO.builder()
                .id(2L)
                .userId(2L)
                .latitude(39.90923)
                .longitude(116.397428)
                .build();

        when(capsuleRepository.findByLatitudeBetweenAndLongitudeBetween(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Arrays.asList(otherUserCapsule));

        boolean result = capsuleService.isCapsuleAtSameLocation(1L, 39.90923, 116.397428, 100.0);

        assertFalse(result);
    }

    @Test
    @DisplayName("获取胶囊详情 - 成功获取")
    void testGetCapsuleDetail_Success() {
        when(capsuleRepository.findById(1L)).thenReturn(Optional.of(testCapsule));
        when(capsuleMediaRepository.findByCapsuleId(anyLong())).thenReturn(new ArrayList<>());

        CapsuleDetailVO result = capsuleService.getCapsuleDetail(1L, 1L);

        assertNotNull(result);
        assertEquals("Test Capsule", result.getTitle());
        assertEquals("Test Content", result.getContent());
        assertTrue(result.getPermissions().getCanEdit());
        assertTrue(result.getPermissions().getCanDelete());
    }

    @Test
    @DisplayName("获取胶囊详情 - 胶囊不存在抛出异常")
    void testGetCapsuleDetail_NotFound() {
        when(capsuleRepository.findById(1L)).thenReturn(Optional.empty());

        IntellitravelException exception = assertThrows(IntellitravelException.class, 
                () -> capsuleService.getCapsuleDetail(1L, 1L));

        assertEquals("Capsule not found", exception.getMessage());
    }

    @Test
    @DisplayName("获取胶囊详情 - 非作者无编辑权限")
    void testGetCapsuleDetail_NonAuthor() {
        when(capsuleRepository.findById(1L)).thenReturn(Optional.of(testCapsule));
        when(capsuleMediaRepository.findByCapsuleId(anyLong())).thenReturn(new ArrayList<>());

        CapsuleDetailVO result = capsuleService.getCapsuleDetail(1L, 2L);

        assertNotNull(result);
        assertFalse(result.getPermissions().getCanEdit());
        assertFalse(result.getPermissions().getCanDelete());
    }

    @Test
    @DisplayName("获取胶囊详情 - 包含图片")
    void testGetCapsuleDetail_WithImages() {
        CapsuleMediaPO media = CapsuleMediaPO.builder()
                .id(1L)
                .capsuleId(1L)
                .url("https://example.com/image.jpg")
                .fileType("image")
                .thumbnail("https://example.com/thumb.jpg")
                .width(800)
                .height(600)
                .build();

        when(capsuleRepository.findById(1L)).thenReturn(Optional.of(testCapsule));
        when(capsuleMediaRepository.findByCapsuleId(anyLong())).thenReturn(Arrays.asList(media));

        CapsuleDetailVO result = capsuleService.getCapsuleDetail(1L, 1L);

        assertNotNull(result);
        assertEquals(1, result.getImages().size());
        assertEquals("https://example.com/image.jpg", result.getImages().get(0).getUrl());
    }

    @Test
    @DisplayName("根据地点获取胶囊 - 使用城市过滤")
    void testGetCapsulesByLocation_WithCity() {
        ObjectNode geocodeNode = objectMapper.createObjectNode();
        geocodeNode.put("formatted_address", "故宫");
        geocodeNode.put("city", "北京市");
        geocodeNode.put("adcode", "110101");
        ArrayNode geocodeArray = objectMapper.createArrayNode();
        geocodeArray.add(geocodeNode);

        when(amapService.getGeocodeInfo(anyString(), eq("北京市"))).thenReturn(geocodeArray);
        when(capsuleRepository.findByAdcode(anyString())).thenReturn(Collections.emptyList());
        when(capsuleRepository.findByCity(anyString())).thenReturn(Arrays.asList(testCapsule));
        when(capsuleMediaRepository.findByCapsuleId(anyLong())).thenReturn(new ArrayList<>());

        List<CapsuleListItemVO> result = capsuleService.getCapsulesByLocation("故宫", "北京市");

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("根据地点获取胶囊 - 地理编码失败使用原始匹配")
    void testGetCapsulesByLocation_GeocodeFallback() {
        when(amapService.getGeocodeInfo(anyString(), any())).thenReturn(null);
        when(capsuleRepository.findByLocationName(anyString())).thenReturn(Arrays.asList(testCapsule));
        when(capsuleMediaRepository.findByCapsuleId(anyLong())).thenReturn(new ArrayList<>());

        List<CapsuleListItemVO> result = capsuleService.getCapsulesByLocation("故宫");

        assertNotNull(result);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("更新时间胶囊 - 更新位置信息")
    void testUpdateCapsule_WithLocation() {
        LocationVO location = LocationVO.builder()
                .name("天安门")
                .city("北京市")
                .build();
        testCapsuleDetail.setLocation(location);

        ObjectNode geocodeNode = objectMapper.createObjectNode();
        geocodeNode.put("formatted_address", "北京市东城区天安门");
        geocodeNode.put("city", "北京市");
        geocodeNode.put("adcode", "110101");
        geocodeNode.put("location", "116.397428,39.90923");
        ArrayNode geocodeArray = objectMapper.createArrayNode();
        geocodeArray.add(geocodeNode);

        when(capsuleRepository.findById(1L)).thenReturn(Optional.of(testCapsule));
        when(amapService.getGeocodeInfo(anyString(), any())).thenReturn(geocodeArray);
        when(capsuleRepository.save(any(CapsulePO.class))).thenReturn(testCapsule);

        assertDoesNotThrow(() -> capsuleService.updateCapsule(1L, 1L, testCapsuleDetail));

        verify(amapService, times(1)).getGeocodeInfo(anyString(), any());
    }
}
