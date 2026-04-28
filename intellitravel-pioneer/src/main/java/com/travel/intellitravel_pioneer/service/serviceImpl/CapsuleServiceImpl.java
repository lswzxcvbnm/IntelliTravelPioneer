package com.travel.intellitravel_pioneer.service.serviceImpl;

import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import com.travel.intellitravel_pioneer.po.CapsuleMediaPO;
import com.travel.intellitravel_pioneer.po.CapsulePO;
import com.travel.intellitravel_pioneer.repository.CapsuleMediaRepository;
import com.travel.intellitravel_pioneer.repository.CapsuleRepository;
import com.travel.intellitravel_pioneer.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.travel.intellitravel_pioneer.service.AmapService;
import com.travel.intellitravel_pioneer.service.CapsuleService;
import com.travel.intellitravel_pioneer.vo.capsule.*;
import com.travel.intellitravel_pioneer.vo.user.UserSimpleVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CapsuleServiceImpl implements CapsuleService {

    @Autowired
    private CapsuleRepository capsuleRepository;

    @Autowired
    private CapsuleMediaRepository capsuleMediaRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AmapService amapService;

    @Override
    @Transactional
    public String createCapsule(Long userId, CapsuleDetailVO capsuleDetail) {
        CapsulePO capsulePO = CapsulePO.builder()
                .userId(userId)
                .title(capsuleDetail.getTitle())
                .content(capsuleDetail.getContent())
                .decoration(capsuleDetail.getDecoration())
                .color(capsuleDetail.getColor())
                .isPublic(capsuleDetail.getIsPublic() != null ? capsuleDetail.getIsPublic() : true)
                .capsuleDate(capsuleDetail.getDate())
                .build();

        if (capsuleDetail.getLocation() != null) {
            fillCapsuleLocationWithGeocode(capsulePO, capsuleDetail.getLocation());
        }

        CapsulePO savedCapsule = capsuleRepository.save(capsulePO);

        if (capsuleDetail.getImages() != null && !capsuleDetail.getImages().isEmpty()) {
            List<CapsuleMediaPO> mediaList = capsuleDetail.getImages().stream().map(image -> 
                CapsuleMediaPO.builder()
                    .capsuleId(savedCapsule.getId())
                    .url(image.getUrl())
                    .fileType(image.getType())
                    .thumbnail(image.getThumbnail())
                    .width(image.getWidth())
                    .height(image.getHeight())
                    .build()
            ).collect(Collectors.toList());
            capsuleMediaRepository.saveAll(mediaList);
        }

        return savedCapsule.getId().toString();
    }

    @Override
    @Transactional
    public void deleteCapsule(Long capsuleId, Long userId) {
        CapsulePO capsule = capsuleRepository.findById(capsuleId)
                .orElseThrow(() -> new IntellitravelException("Capsule not found"));

        if (!capsule.getUserId().equals(userId)) {
            throw new IntellitravelException("Not authorized to delete this capsule");
        }

        capsuleMediaRepository.deleteByCapsuleId(capsuleId);
        capsuleRepository.delete(capsule);
    }

    @Override
    @Transactional
    public void updateCapsule(Long capsuleId, Long userId, CapsuleDetailVO capsuleDetail) {
        CapsulePO capsule = capsuleRepository.findById(capsuleId)
                .orElseThrow(() -> new IntellitravelException("Capsule not found"));

        if (!capsule.getUserId().equals(userId)) {
            throw new IntellitravelException("Not authorized to update this capsule");
        }

        capsule.setTitle(capsuleDetail.getTitle() != null ? capsuleDetail.getTitle() : capsule.getTitle());
        capsule.setContent(capsuleDetail.getContent() != null ? capsuleDetail.getContent() : capsule.getContent());
        capsule.setDecoration(capsuleDetail.getDecoration() != null ? capsuleDetail.getDecoration() : capsule.getDecoration());
        capsule.setColor(capsuleDetail.getColor() != null ? capsuleDetail.getColor() : capsule.getColor());
        capsule.setIsPublic(capsuleDetail.getIsPublic() != null ? capsuleDetail.getIsPublic() : capsule.getIsPublic());
        capsule.setCapsuleDate(capsuleDetail.getDate() != null ? capsuleDetail.getDate() : capsule.getCapsuleDate());

        if (capsuleDetail.getLocation() != null) {
            fillCapsuleLocationWithGeocode(capsule, capsuleDetail.getLocation());
        }

        capsuleRepository.save(capsule);

        if (capsuleDetail.getImages() != null) {
            // 删除旧的图片
            capsuleMediaRepository.deleteByCapsuleId(capsuleId);
            
            // 插入新的图片
            List<CapsuleMediaPO> mediaList = capsuleDetail.getImages().stream().map(image -> 
                CapsuleMediaPO.builder()
                    .capsuleId(capsule.getId())
                    .url(image.getUrl())
                    .fileType(image.getType())
                    .thumbnail(image.getThumbnail())
                    .width(image.getWidth())
                    .height(image.getHeight())
                    .build()
            ).collect(Collectors.toList());
            capsuleMediaRepository.saveAll(mediaList);
        }
    }

    @Override
    public List<CapsuleListItemVO> getUserCapsules(Long userId) {
        List<CapsulePO> capsules = capsuleRepository.findByUserId(userId);
        return capsules.stream().map(this::convertToListItemVO).collect(Collectors.toList());
    }

    @Override
    public List<CapsuleListItemVO> getCapsulesByLocation(String locationName) {
        return getCapsulesByLocation(locationName, null);
    }

    @Override
    public List<CapsuleListItemVO> getCapsulesByLocation(String locationName, String city) {
        if (locationName == null || locationName.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String source = locationName.trim();
        String cityTrimmed = city != null ? city.trim() : null;

        List<CapsulePO> capsules = Collections.emptyList();

        JsonNode geocode = amapService.getGeocodeInfo(source, cityTrimmed);
        String formatted = null;
        String adcode = null;

        if (geocode != null && geocode.isArray() && geocode.size() > 0) {
            JsonNode first = geocode.get(0);
            formatted = first.path("formatted_address").asText("");
            String geocodeCity = first.path("city").asText("");
            adcode = first.path("adcode").asText("");

            // 1) adcode 精准匹配（最严格）
            if (!adcode.isEmpty()) {
                capsules = capsuleRepository.findByAdcode(adcode);
                if (!capsules.isEmpty()) {
                    return capsules.stream().map(this::convertToListItemVO).collect(Collectors.toList());
                }
            }

            // 2) city 严格优先匹配（目标城市优先）
            String cityForSearch = cityTrimmed != null && !cityTrimmed.isEmpty() ? cityTrimmed : geocodeCity;
            if (cityForSearch != null && !cityForSearch.isEmpty()) {
                capsules = capsuleRepository.findByCity(cityForSearch);
                if (!capsules.isEmpty()) {
                    return capsules.stream().map(this::convertToListItemVO).collect(Collectors.toList());
                }
            }

            // 3) 同 city 下按关键字搜索
            if (cityForSearch != null && !cityForSearch.isEmpty() && formatted != null && !formatted.isEmpty()) {
                capsules = capsuleRepository.findByCityAndKeyword(cityForSearch, formatted);
                if (!capsules.isEmpty()) {
                    return capsules.stream().map(this::convertToListItemVO).collect(Collectors.toList());
                }
            }

            // 4) 无 city/adcode 约束时，仅按标准名称查
            if (formatted != null && !formatted.isEmpty()) {
                capsules = capsuleRepository.findByLocationNameContainingOrAddressContaining(formatted, formatted);
                if (!capsules.isEmpty()) {
                    return capsules.stream().map(this::convertToListItemVO).collect(Collectors.toList());
                }
            }
        }

        // 兜底原始匹配：1）locationName 1:1，2）模糊
        capsules = capsuleRepository.findByLocationName(source);
        if (capsules.isEmpty()) {
            capsules = capsuleRepository.findByLocationNameContainingOrAddressContaining(source, source);
        }

        return capsules.stream().map(this::convertToListItemVO).collect(Collectors.toList());
    }

    @Override
    public List<CapsuleListItemVO> getCapsulesByLocation(Double latitude, Double longitude, Double radiusMeters) {
        if (latitude == null || longitude == null || radiusMeters == null || radiusMeters <= 0) {
            throw new IllegalArgumentException("latitude、longitude 和 radiusMeters 必须有效");
        }

        double km = radiusMeters / 1000.0;
        double latDelta = km / 111.0;
        double lngDelta = km / (111.0 * Math.cos(Math.toRadians(latitude)));

        double minLat = latitude - latDelta;
        double maxLat = latitude + latDelta;
        double minLng = longitude - lngDelta;
        double maxLng = longitude + lngDelta;

        List<CapsulePO> candidates = capsuleRepository.findByLatitudeBetweenAndLongitudeBetween(minLat, maxLat, minLng, maxLng);

        return candidates.stream()
                .filter(c -> c.getLatitude() != null && c.getLongitude() != null)
                .filter(c -> haversineDistanceMeters(latitude, longitude, c.getLatitude(), c.getLongitude()) <= radiusMeters)
                .map(this::convertToListItemVO)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isCapsuleAtSameLocation(Long userId, Double latitude, Double longitude, Double radiusMeters) {
        if (latitude == null || longitude == null || userId == null) {
            return false;
        }

        // 近似 bounding box 过滤，减少计算量
        double km = radiusMeters / 1000.0;
        double latDelta = km / 111.0; // 1° 纬度≈111km
        double lngDelta = km / (111.0 * Math.cos(Math.toRadians(latitude))); // 1° 经度随纬度变化

        double minLat = latitude - latDelta;
        double maxLat = latitude + latDelta;
        double minLng = longitude - lngDelta;
        double maxLng = longitude + lngDelta;

        List<CapsulePO> candidates = capsuleRepository.findByLatitudeBetweenAndLongitudeBetween(minLat, maxLat, minLng, maxLng);

        for (CapsulePO candidate : candidates) {
            if (!candidate.getUserId().equals(userId)) {
                continue;
            }
            if (candidate.getLatitude() == null || candidate.getLongitude() == null) {
                continue;
            }
            double distance = haversineDistanceMeters(latitude, longitude, candidate.getLatitude(), candidate.getLongitude());
            if (distance <= radiusMeters) {
                return true;
            }
        }
        return false;
    }

    /**
     * 用高德地理编码或 POI 结果标准化位置并填充 capsulePO
     */
    private void fillCapsuleLocationWithGeocode(CapsulePO capsulePO, LocationVO locationInput) {
        if (locationInput == null) {
            return;
        }

        String query = locationInput.getName();
        if (query == null || query.trim().isEmpty()) {
            query = locationInput.getAddress();
        }

        JsonNode geocode = null;
        if (query != null && !query.trim().isEmpty()) {
            geocode = amapService.getGeocodeInfo(query, locationInput.getCity());
        }

        if (geocode != null && geocode.isArray() && geocode.size() > 0) {
            JsonNode first = geocode.get(0);
            String formatted = first.path("formatted_address").asText("");
            String city = first.path("city").asText("");
            String adcode = first.path("adcode").asText("");
            String location = first.path("location").asText("");

            if (!formatted.isEmpty()) {
                String locationName = formatted;
                if (locationInput.getName() != null && !locationInput.getName().trim().isEmpty() && !formatted.contains(locationInput.getName().trim())) {
                    locationName = locationInput.getName().trim() + " " + formatted;
                }
                capsulePO.setLocationName(locationName);
                capsulePO.setAddress(formatted);
            }
            if (!city.isEmpty()) {
                capsulePO.setCity(city);
            }
            if (!adcode.isEmpty()) {
                capsulePO.setAdcode(adcode);
            }
            if (location != null && !location.isEmpty() && location.contains(",")) {
                try {
                    String[] coords = location.split(",");
                    capsulePO.setLongitude(Double.parseDouble(coords[0]));
                    capsulePO.setLatitude(Double.parseDouble(coords[1]));
                } catch (NumberFormatException ignored) {
                }
            }

            // 同步入现有字段 country 并仍保留输入字段兜底
            capsulePO.setCountry(locationInput.getCountry() != null ? locationInput.getCountry() : capsulePO.getCountry());
            if (capsulePO.getLocationName() == null || capsulePO.getLocationName().isEmpty()) {
                capsulePO.setLocationName(locationInput.getName());
            }
            if (capsulePO.getAddress() == null || capsulePO.getAddress().isEmpty()) {
                capsulePO.setAddress(locationInput.getAddress());
            }

            return;
        }

        // 兜底：如果高德返回失败，则尽量使用请求的内容
        capsulePO.setLocationName(locationInput.getName());
        capsulePO.setAddress(locationInput.getAddress());
        capsulePO.setLatitude(locationInput.getLat());
        capsulePO.setLongitude(locationInput.getLng());
        capsulePO.setCity(locationInput.getCity());
        capsulePO.setCountry(locationInput.getCountry());
    }

    private double haversineDistanceMeters(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371000; // 地球半径，单位：米
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    @Override
    public CapsuleDetailVO getCapsuleDetail(Long capsuleId, Long currentUserId) {
        CapsulePO capsule = capsuleRepository.findById(capsuleId)
                .orElseThrow(() -> new IntellitravelException("Capsule not found"));

        List<CapsuleMediaPO> mediaList = capsuleMediaRepository.findByCapsuleId(capsuleId);
        
        // fixme: Replace with actual User PO loading instead of relying solely on the ID.
        // Doing simple mock if user doesn't exist just to map the ID.
        UserSimpleVO authorVO = new UserSimpleVO();
        authorVO.setId(capsule.getUserId().toString());
        
        userRepository.findById(capsule.getUserId().intValue()).ifPresent(u -> {
            authorVO.setName(u.getUsername());
            // Map avatar if existed in UserPO
        });

        LocationVO location = LocationVO.builder()
                .name(capsule.getLocationName())
                .address(capsule.getAddress())
                .lat(capsule.getLatitude())
                .lng(capsule.getLongitude())
                .city(capsule.getCity())
                .adcode(capsule.getAdcode())
                .country(capsule.getCountry())
                .build();

        List<MediaFileVO> images = mediaList.stream().map(m -> 
            MediaFileVO.builder()
                .id(m.getId().toString())
                .url(m.getUrl())
                .type(m.getFileType())
                .thumbnail(m.getThumbnail())
                .width(m.getWidth())
                .height(m.getHeight())
                .build()
        ).collect(Collectors.toList());

        boolean isAuthor = currentUserId != null && currentUserId.equals(capsule.getUserId());
        PermissionsVO permissions = PermissionsVO.builder()
                .canEdit(isAuthor)
                .canDelete(isAuthor)
                .canComment(true)
                .build();

        return CapsuleDetailVO.builder()
                .id(capsule.getId().toString())
                .title(capsule.getTitle())
                .content(capsule.getContent())
                .decoration(capsule.getDecoration())
                .color(capsule.getColor())
                .isPublic(capsule.getIsPublic())
                .date(capsule.getCapsuleDate())
                .createdAt(capsule.getCreatedAt() != null ? capsule.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null)
                .author(authorVO)
                .location(location)
                .images(images)
                .likesCount(capsule.getLikesCount())
                .commentsCount(capsule.getCommentsCount())
                .viewCount(capsule.getViewCount())
                .permissions(permissions)
                // Default false for demo; real implementation depends on likes/safes tables
                .isLiked(false)
                .isSaved(false)
                .build();
    }

    private CapsuleListItemVO convertToListItemVO(CapsulePO capsule) {
        CapsuleListItemVO vo = new CapsuleListItemVO();
        vo.setId(capsule.getId().toString());
        vo.setTitle(capsule.getTitle());
        vo.setColor(capsule.getColor());
        vo.setDate(capsule.getCapsuleDate());
        vo.setCreatedAt(capsule.getCreatedAt() != null ? capsule.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
        
        // Find owner to set authorName realistically
        userRepository.findById(capsule.getUserId().intValue()).ifPresent(u -> vo.setAuthorName(u.getUsername()));
        
        // Fetch first image to use as thumbnail
        List<CapsuleMediaPO> mediaList = capsuleMediaRepository.findByCapsuleId(capsule.getId());
        if (!mediaList.isEmpty()) {
            CapsuleMediaPO firstMedia = mediaList.get(0);
            vo.setCoverImage(firstMedia.getThumbnail() != null ? firstMedia.getThumbnail() : firstMedia.getUrl());
        }

        if (capsule.getLocationName() != null || capsule.getLatitude() != null) {
            CapsuleListItemVO.SimpleLocationVO loc = new CapsuleListItemVO.SimpleLocationVO();
            loc.setName(capsule.getLocationName());
            loc.setLat(capsule.getLatitude());
            loc.setLng(capsule.getLongitude());
            vo.setLocation(loc);
        }

        return vo;
    }
}
