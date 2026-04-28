package com.travel.intellitravel_pioneer.service;

import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import com.travel.intellitravel_pioneer.po.RealTimeTripPO;
import com.travel.intellitravel_pioneer.po.TripTrajectoryMapPO;
import com.travel.intellitravel_pioneer.po.TripTrajectoryPointPO;
import com.travel.intellitravel_pioneer.po.TripReportPO;
import com.travel.intellitravel_pioneer.repository.RealTimeTripRepository;
import com.travel.intellitravel_pioneer.repository.TripTrajectoryMapRepository;
import com.travel.intellitravel_pioneer.repository.TripTrajectoryPointRepository;
import com.travel.intellitravel_pioneer.repository.TripReportRepository;
import com.travel.intellitravel_pioneer.util.OssUtil;
import com.travel.intellitravel_pioneer.vo.trip.GenerateTripReportRequest;
import com.travel.intellitravel_pioneer.vo.trip.ReportData;
import com.travel.intellitravel_pioneer.vo.trip.UserRealTimeStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.travel.intellitravel_pioneer.vo.trip.ReportSummary;

@Service
public class ReportGeneratorService {

    @Autowired
    private HtmlToImageService htmlToImageService;

    @Autowired
    private OssUtil ossUtil;

    @Autowired
    private TripReportRepository tripReportRepository;

    @Autowired
    private RealTimeTripRepository realTimeTripRepository;

    @Autowired
    private TripTrajectoryMapRepository tripTrajectoryMapRepository;

    @Autowired
    private TripTrajectoryPointRepository tripTrajectoryPointRepository;

    /**
     * 基于后端已有的行程数据构建战报数据。
     * 当前版本尽量从后端补齐旧字段，request中的 include_content/custom_text 暂不影响生图。
     */
    public ReportData buildReportDataFromBackend(Integer userId, String tripId, GenerateTripReportRequest request) {
        if (userId == null) {
            throw new IntellitravelException("user_id is required");
        }
        if (tripId == null || tripId.isBlank()) {
            throw new IntellitravelException("trip_id is required");
        }

        String userIdStr = String.valueOf(userId);
        TripTrajectoryMapPO mapPO = tripTrajectoryMapRepository.findByTripIdAndUserId(tripId, userIdStr);
        if (mapPO == null) {
            throw new IntellitravelException("trip not found");
        }

        RealTimeTripPO tripPO = realTimeTripRepository.findByTripId(tripId);
        UserRealTimeStatus lastStatus = null;
        if (tripPO != null
            && userIdStr.equals(tripPO.getUserId())
            && tripPO.getLastStatus() != null
            && tripId.equals(tripPO.getLastStatus().getTripId())) {
            lastStatus = tripPO.getLastStatus();
        }

        List<TripTrajectoryPointPO> trajectoryPoints = tripTrajectoryPointRepository.findByTripIdOrderBySeqAsc(tripId);

        ReportData reportData = new ReportData();
        reportData.setTrajectoryUrl(mapPO.getTrajectoryMapUrl());
        reportData.setSteps(lastStatus != null && lastStatus.getSteps() != null ? lastStatus.getSteps() : 0);
        reportData.setPowerCost(lastStatus != null && lastStatus.getPowerCost() != null ? lastStatus.getPowerCost() : 0);
        reportData.setDurationMin(calculateDurationMinutes(trajectoryPoints));
        reportData.setTheme(mapStyleToTheme(request == null ? null : request.getStyle()));
        return reportData;
    }

    /**
     * 生成战报海报、上传OSS并记录在数据库
     */
    public String generateAndSaveReport(Integer userId, String tripId, ReportData data, String templateName) throws Exception {
        
        // 1. 根据行程数据、模板进行生图，返回图片字节流数组
        byte[] imageBytes = htmlToImageService.generateJourneyReportPoster(data, templateName);
        
        // 2. 将 byte 数组转为 InputStream 供 OssUtil 调用
        InputStream inputStream = new ByteArrayInputStream(imageBytes);

        // 3. 构建在 OSS 中的对象名
        String objectName = "trip-reports/" + tripId + "_" + UUID.randomUUID().toString() + ".jpg";

        // 4. 调用由于本项目已经封装好的 OSSUtil 上传图片
        String reportUrl = ossUtil.upload(objectName, inputStream);

        // 5. 生成结果保存到 MySQL / 本地记录
        TripReportPO tripReportParams = new TripReportPO();
        tripReportParams.setUserId(userId);
        tripReportParams.setTripId(tripId);
        tripReportParams.setReportImageUrl(reportUrl);
        tripReportParams.setTemplateId(templateName);
        tripReportParams.setCreatedAt(OffsetDateTime.now());
        
        tripReportRepository.save(tripReportParams);

        // 返回 OSS 的图片访问链接
        return reportUrl;
    }

    /**
     * 查询战报列表（支持分页、时间区间和模板过滤）
     */
    public List<ReportSummary> queryReports(Integer userId,
                                            String tripId,
                                            int page,
                                            int size,
                                            OffsetDateTime from,
                                            OffsetDateTime to,
                                            String templateId,
                                            boolean includeImage) {
        if (userId == null) {
            throw new IntellitravelException("user_id is required");
        }
        if (tripId == null || tripId.isBlank()) {
            throw new IntellitravelException("trip_id is required");
        }

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<TripReportPO> pageRes = tripReportRepository.findAllByTripIdAndUserIdOrderByCreatedAtDesc(tripId, userId, pageable);

        return pageRes.stream()
                .filter(po -> {
                    if (from != null && po.getCreatedAt() != null && po.getCreatedAt().isBefore(from)) {
                        return false;
                    }
                    if (to != null && po.getCreatedAt() != null && po.getCreatedAt().isAfter(to)) {
                        return false;
                    }
                    if (templateId != null && !templateId.isBlank() && !templateId.equals(po.getTemplateId())) {
                        return false;
                    }
                    return true;
                })
                .map(po -> {
                    ReportSummary s = new ReportSummary();
                    s.setId(po.getId());
                    s.setTemplateId(po.getTemplateId());
                    s.setCreatedAt(po.getCreatedAt());
                    s.setReportImageUrl(includeImage ? po.getReportImageUrl() : null);
                    return s;
                })
                .collect(Collectors.toList());
    }

    private Integer calculateDurationMinutes(List<TripTrajectoryPointPO> points) {
        if (points == null || points.size() < 2) {
            return 0;
        }

        OffsetDateTime first = points.get(0).getSampledAt();
        OffsetDateTime last = points.get(points.size() - 1).getSampledAt();
        if (first != null && last != null) {
            long minutes = Duration.between(first, last).toMinutes();
            return (int) Math.max(0, minutes);
        }

        return 0;
    }

    private String mapStyleToTheme(String style) {
        if (style == null || style.isBlank()) {
            return "nature";
        }
        String normalizedStyle = style.trim().toLowerCase(Locale.ROOT);
        switch (normalizedStyle) {
            case "nature":
            case "city":
            case "art":
            case "retro":
                return normalizedStyle;
            default:
                return "nature";
        }
    }
}



