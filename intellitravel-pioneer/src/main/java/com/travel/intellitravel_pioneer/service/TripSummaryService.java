package com.travel.intellitravel_pioneer.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import com.travel.intellitravel_pioneer.po.ItineraryPO;
import com.travel.intellitravel_pioneer.po.RealTimeTripPO;
import com.travel.intellitravel_pioneer.po.TripSummaryPO;
import com.travel.intellitravel_pioneer.po.TripTrajectoryMapPO;
import com.travel.intellitravel_pioneer.po.TripTrajectoryPointPO;
import com.travel.intellitravel_pioneer.po.UserPO;
import com.travel.intellitravel_pioneer.repository.ItineraryRepository;
import com.travel.intellitravel_pioneer.repository.RealTimeTripRepository;
import com.travel.intellitravel_pioneer.repository.TripSummaryRepository;
import com.travel.intellitravel_pioneer.repository.TripTrajectoryMapRepository;
import com.travel.intellitravel_pioneer.repository.TripTrajectoryPointRepository;
import com.travel.intellitravel_pioneer.service.ai.TripSummaryAiClient;
import com.travel.intellitravel_pioneer.util.TokenUtil;
import com.travel.intellitravel_pioneer.vo.itinerary.DayPlan;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryPlan;
import com.travel.intellitravel_pioneer.vo.itinerary.RouteItem;
import com.travel.intellitravel_pioneer.vo.trip.Location;
import com.travel.intellitravel_pioneer.vo.trip.TripSummaryListItem;
import com.travel.intellitravel_pioneer.vo.trip.TripSummaryAiRequest;
import com.travel.intellitravel_pioneer.vo.trip.TripSummaryAiResult;
import com.travel.intellitravel_pioneer.vo.trip.TripSummaryGenerationStatusResponse;
import com.travel.intellitravel_pioneer.vo.trip.TripSummaryResponse;
import com.travel.intellitravel_pioneer.vo.trip.UserRealTimeStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class TripSummaryService {

    private static final Logger log = LoggerFactory.getLogger(TripSummaryService.class);

    private static final int MAX_SUMMARY_TRAJECTORY_POINTS = 12;
    private static final int MAX_PLANNED_HIGHLIGHTS = 6;
    private static final int MAX_DAILY_OUTLINE = 10;
    private static final int MAX_NEARBY_POIS = 5;
    private static final int MAX_ITINERARY_RAW_LENGTH = 6000;
    private static final ConcurrentHashMap<String, ReentrantLock> SUMMARY_LOCKS = new ConcurrentHashMap<>();

    private final TripSummaryRepository tripSummaryRepository;
    private final TripTrajectoryMapRepository tripTrajectoryMapRepository;
    private final TripTrajectoryPointRepository tripTrajectoryPointRepository;
    private final ItineraryRepository itineraryRepository;
    private final RealTimeTripRepository realTimeTripRepository;
    private final AmapService amapService;
    private final ObjectMapper objectMapper;
    private final TripSummaryAiClient tripSummaryAiClient;
    private final TokenUtil tokenUtil;
    private final TripSummaryGenerationStateStore tripSummaryGenerationStateStore;

    public TripSummaryService(TripSummaryRepository tripSummaryRepository,
                              TripTrajectoryMapRepository tripTrajectoryMapRepository,
                              TripTrajectoryPointRepository tripTrajectoryPointRepository,
                              ItineraryRepository itineraryRepository,
                              RealTimeTripRepository realTimeTripRepository,
                              AmapService amapService,
                              ObjectMapper objectMapper,
                              TripSummaryAiClient tripSummaryAiClient,
                              TokenUtil tokenUtil,
                              TripSummaryGenerationStateStore tripSummaryGenerationStateStore) {
        this.tripSummaryRepository = tripSummaryRepository;
        this.tripTrajectoryMapRepository = tripTrajectoryMapRepository;
        this.tripTrajectoryPointRepository = tripTrajectoryPointRepository;
        this.itineraryRepository = itineraryRepository;
        this.realTimeTripRepository = realTimeTripRepository;
        this.amapService = amapService;
        this.objectMapper = objectMapper;
        this.tripSummaryAiClient = tripSummaryAiClient;
        this.tokenUtil = tokenUtil;
        this.tripSummaryGenerationStateStore = tripSummaryGenerationStateStore;
    }

    @Transactional
    public TripSummaryResponse generateSummary(String token, String tripId, boolean forceRegenerate) {
        UserPO userPO = resolveUser(token);
        String userId = String.valueOf(userPO.getId());
        TripTrajectoryMapPO tripMap = findOwnedTrip(tripId, userId);
        log.info("Generate trip summary requested: userId={}, tripId={}, forceRegenerate={}", userPO.getId(), tripId,
            forceRegenerate);
        RealTimeTripPO realTimeTripPO = realTimeTripRepository.findByTripId(tripId);
        UserRealTimeStatus finalStatus = resolveFinalStatus(tripMap, userId);
        ensureTripCompleted(tripMap, finalStatus, realTimeTripPO);

        ReentrantLock lock = SUMMARY_LOCKS.computeIfAbsent(tripId, key -> new ReentrantLock());
        lock.lock();
        try {
            TripSummaryPO existing = tripSummaryRepository.findByTripIdAndUserId(tripId, userPO.getId());
            if (existing != null && !forceRegenerate) {
                return toResponse(existing);
            }

            SummarySnapshot snapshot = buildSummarySnapshot(tripMap, finalStatus);
            TripSummaryAiResult aiResult = tripSummaryAiClient.summarizeTrip(token, snapshot.aiRequest);

            TripSummaryPO summaryPO = existing == null ? new TripSummaryPO() : existing;
            if (summaryPO.getSummaryId() == null || summaryPO.getSummaryId().isBlank()) {
                summaryPO.setSummaryId(UUID.randomUUID().toString().replace("-", ""));
            }
            summaryPO.setUserId(userPO.getId());
            summaryPO.setTripId(tripId);
            summaryPO.setItineraryId(tripMap.getItineraryId());
            summaryPO.setTitle(aiResult.getTitle());
            summaryPO.setSummaryText(aiResult.getSummary());
            summaryPO.setDurationMinutes(snapshot.durationMinutes);
            summaryPO.setTotalDistanceMeters(snapshot.totalDistanceMeters);
            summaryPO.setTrajectoryPointCount(snapshot.trajectoryPointCount);
            summaryPO.setFinalSteps(snapshot.finalSteps);
            summaryPO.setFinalCurrentPower(snapshot.finalCurrentPower);
            summaryPO.setFinalTotalPower(snapshot.finalTotalPower);

            try {
                return toResponse(tripSummaryRepository.save(summaryPO));
            } catch (DataIntegrityViolationException ex) {
                TripSummaryPO concurrent = tripSummaryRepository.findByTripIdAndUserId(tripId, userPO.getId());
                if (concurrent != null) {
                    log.warn("Trip summary duplicate insert avoided by fallback read: userId={}, tripId={}",
                            userPO.getId(), tripId);
                    return toResponse(concurrent);
                }
                throw ex;
            }
        } finally {
            lock.unlock();
            if (!lock.hasQueuedThreads()) {
                SUMMARY_LOCKS.remove(tripId, lock);
            }
        }
    }

    @Transactional(readOnly = true)
    public TripSummaryResponse getSummary(String token, String tripId) {
        UserPO userPO = resolveUser(token);
        TripSummaryPO summaryPO = tripSummaryRepository.findByTripIdAndUserId(tripId, userPO.getId());
        if (summaryPO == null) {
            throw new IntellitravelException("trip summary not found");
        }
        return toResponse(summaryPO);
    }

    @Transactional(readOnly = true)
    public TripSummaryResponse getSummaryBySummaryId(String token, String summaryId) {
        if (summaryId == null || summaryId.isBlank()) {
            throw new IntellitravelException("summary_id is required");
        }
        // Public lookup: summary visibility is controlled by unpredictable summary_id.
        // token is intentionally ignored to avoid user-scope filtering.
        TripSummaryPO summaryPO = tripSummaryRepository.findBySummaryId(summaryId);
        if (summaryPO == null) {
            throw new IntellitravelException("trip summary not found");
        }
        return toResponse(summaryPO);
    }

    @Transactional(readOnly = true)
    public TripSummaryGenerationStatusResponse getSummaryGenerationStatus(String token, String tripId) {
        UserPO userPO = resolveUser(token);
        String userId = String.valueOf(userPO.getId());
        findOwnedTrip(tripId, userId);

        TripSummaryPO summaryPO = tripSummaryRepository.findByTripIdAndUserId(tripId, userPO.getId());
        RealTimeTripPO realTimeTripPO = realTimeTripRepository.findByTripId(tripId);
        TripSummaryGenerationStateStore.StateSnapshot snapshot =
                tripSummaryGenerationStateStore.getSnapshot(tripId).orElse(null);

        TripSummaryGenerationStatusResponse response = new TripSummaryGenerationStatusResponse();
        response.setTripId(tripId);
        response.setHasSummary(summaryPO != null);
        response.setIsCompleted(realTimeTripPO != null && Boolean.TRUE.equals(realTimeTripPO.getIsCompleted()));

        if (summaryPO != null) {
            response.setStatus("GENERATED");
            response.setFailureMessage(null);
            response.setLastUpdatedAt(summaryPO.getUpdatedAt() != null ? summaryPO.getUpdatedAt() : summaryPO.getCreatedAt());
            return response;
        }

        if (snapshot == null) {
            response.setStatus("NOT_GENERATED");
            response.setFailureMessage(null);
            response.setLastUpdatedAt(null);
            return response;
        }

        switch (snapshot.status()) {
            case IN_PROGRESS -> {
                response.setStatus("IN_PROGRESS");
                response.setFailureMessage(null);
            }
            case FAILED -> {
                response.setStatus("FAILED");
                response.setFailureMessage(snapshot.errorMessage());
            }
            case SUCCEEDED -> {
                response.setStatus("NOT_GENERATED");
                response.setFailureMessage(null);
            }
            default -> {
                response.setStatus("NOT_GENERATED");
                response.setFailureMessage(null);
            }
        }
        response.setLastUpdatedAt(snapshot.updatedAt());
        return response;
    }

    @Transactional(readOnly = true)
    public List<TripSummaryListItem> listSummaries(String token,
                                                   int page,
                                                   int size,
                                                   OffsetDateTime from,
                                                   OffsetDateTime to,
                                                   String itineraryId,
                                                   boolean includeContent) {
        UserPO userPO = resolveUser(token);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "createdAt"));
        List<TripSummaryPO> summaries = tripSummaryRepository.findAllByUserIdOrderByCreatedAtDesc(userPO.getId(), pageable).getContent();

        List<TripSummaryListItem> results = new ArrayList<>();
        for (TripSummaryPO summaryPO : summaries) {
            if (from != null && summaryPO.getCreatedAt() != null && summaryPO.getCreatedAt().isBefore(from)) {
                continue;
            }
            if (to != null && summaryPO.getCreatedAt() != null && summaryPO.getCreatedAt().isAfter(to)) {
                continue;
            }
            if (itineraryId != null && !itineraryId.isBlank() && !itineraryId.equals(summaryPO.getItineraryId())) {
                continue;
            }
            results.add(toListItem(summaryPO, includeContent));
        }
        return results;
    }

    private UserPO resolveUser(String token) {
        try {
            UserPO userPO = tokenUtil.getUser(token);
            if (userPO == null) {
                throw new IntellitravelException("invalid token");
            }
            return userPO;
        } catch (Exception e) {
            throw new IntellitravelException("invalid token");
        }
    }

    private TripTrajectoryMapPO findOwnedTrip(String tripId, String userId) {
        if (tripId == null || tripId.isBlank()) {
            throw new IntellitravelException("trip_id is required");
        }
        TripTrajectoryMapPO tripMap = tripTrajectoryMapRepository.findByTripIdAndUserId(tripId, userId);
        if (tripMap == null) {
            log.warn("Trip not found when generating summary: tripId={}, userId={}", tripId, userId);
            throw new IntellitravelException("trip not found");
        }
        return tripMap;
    }

    private void ensureTripCompleted(TripTrajectoryMapPO tripMap, UserRealTimeStatus finalStatus, RealTimeTripPO realTimeTripPO) {
        if (finalStatus == null) {
            log.error(
                    "Trip status missing for summary generation: tripId={}, userId={}, itineraryId={}, mapCreatedAt={}, mapUpdatedAt={}",
                    tripMap.getTripId(), tripMap.getUserId(), tripMap.getItineraryId(), tripMap.getCreatedAt(),
                    tripMap.getUpdatedAt());
            throw new IntellitravelException(
                    "trip status not found (trip_id=" + tripMap.getTripId() + ")，请先上报一次状态或结束行程后重试");
        }
        if (realTimeTripPO == null || !Boolean.TRUE.equals(realTimeTripPO.getIsCompleted())) {
            log.warn("Trip is still active for summary generation: tripId={}, userId={}, isCompleted={}",
                    tripMap.getTripId(), tripMap.getUserId(), realTimeTripPO == null ? null : realTimeTripPO.getIsCompleted());
            throw new IntellitravelException("trip is still active, cannot generate summary before it ends");
        }
    }

    private UserRealTimeStatus resolveFinalStatus(TripTrajectoryMapPO tripMap, String userId) {
        RealTimeTripPO realTimeTripPO = realTimeTripRepository.findByTripId(tripMap.getTripId());
        if (realTimeTripPO == null || realTimeTripPO.getLastStatus() == null) {
            log.warn("No realtime status found for summary generation: tripId={}, userId={}", tripMap.getTripId(),
                    userId);
            return null;
        }

        if (!userId.equals(realTimeTripPO.getUserId())) {
            log.warn("Realtime trip ownership mismatch for summary generation: tripId={}, expectedUserId={}, actualUserId={}",
                    tripMap.getTripId(), userId, realTimeTripPO.getUserId());
            return null;
        }

        UserRealTimeStatus fallbackStatus = realTimeTripPO.getLastStatus();
        if (!tripMap.getTripId().equals(fallbackStatus.getTripId())) {
            log.warn("Realtime trip fallback status tripId mismatch: expectedTripId={}, realtimeTripId={}, userId={}",
                    tripMap.getTripId(), fallbackStatus.getTripId(), userId);
            return null;
        }

        log.info("Using real_time_trip.last_status for summary generation: tripId={}, userId={}",
                tripMap.getTripId(), userId);
        return fallbackStatus;
    }

    private SummarySnapshot buildSummarySnapshot(TripTrajectoryMapPO tripMap, UserRealTimeStatus finalStatus) {
        ItineraryPO itineraryPO = loadItinerary(tripMap.getItineraryId());
        ItineraryPlan plan = itineraryPO == null ? null : itineraryPO.getPlan();
        List<TripTrajectoryPointPO> points = tripTrajectoryPointRepository.findByTripIdOrderBySeqAsc(tripMap.getTripId());

        Integer durationMinutes = calculateDurationMinutes(points, finalStatus, tripMap.getCreatedAt());
        Long totalDistanceMeters = calculateTotalDistanceMeters(points);
        Integer trajectoryPointCount = points.size();
        List<Location> sampledTrajectory = sampleTrajectory(points, MAX_SUMMARY_TRAJECTORY_POINTS);
        List<String> plannedHighlights = extractPlannedHighlights(plan);
        List<String> itineraryDailyOutline = extractItineraryDailyOutline(plan);
        String itineraryRaw = buildItineraryRaw(plan);
        String finalLocationText = buildFinalLocationText(finalStatus);
        List<String> nearbyPois = buildNearbyPoiHighlights(finalStatus);

        TripSummaryAiRequest request = new TripSummaryAiRequest();
        request.setTripId(tripMap.getTripId());
        request.setItineraryId(tripMap.getItineraryId());
        request.setItineraryTitle(itineraryPO == null ? null : itineraryPO.getTitle());
        request.setDestination(plan == null ? null : plan.getDestination());
        request.setTotalDays(plan == null ? null : plan.getTotalDays());
        request.setPlannedHighlights(plannedHighlights);
        request.setItineraryDailyOutline(itineraryDailyOutline);
        request.setItineraryRaw(itineraryRaw);
        request.setDurationMinutes(durationMinutes);
        request.setTotalDistanceMeters(totalDistanceMeters);
        request.setTrajectoryPointCount(trajectoryPointCount);
        request.setSampledTrajectory(sampledTrajectory);
        request.setFinalLocationText(finalLocationText);
        request.setNearbyPois(nearbyPois);
        request.setFinalStatus(finalStatus);

        SummarySnapshot snapshot = new SummarySnapshot();
        snapshot.aiRequest = request;
        snapshot.durationMinutes = durationMinutes;
        snapshot.totalDistanceMeters = totalDistanceMeters;
        snapshot.trajectoryPointCount = trajectoryPointCount;
        snapshot.finalSteps = finalStatus == null ? null : finalStatus.getSteps();
        snapshot.finalCurrentPower = finalStatus == null ? null : finalStatus.getCurrentPower();
        snapshot.finalTotalPower = finalStatus == null ? null : finalStatus.getTotalPower();
        return snapshot;
    }

    private ItineraryPO loadItinerary(String itineraryId) {
        if (itineraryId == null || itineraryId.isBlank()) {
            return null;
        }
        Optional<ItineraryPO> itineraryPO = itineraryRepository.findById(itineraryId);
        return itineraryPO.orElse(null);
    }

    private Integer calculateDurationMinutes(List<TripTrajectoryPointPO> points,
                                             UserRealTimeStatus finalStatus,
                                             LocalDateTime createdAt) {
        if (points != null && points.size() >= 2) {
            return Math.max(0, (int) Duration.between(points.get(0).getSampledAt(), points.get(points.size() - 1).getSampledAt()).toMinutes());
        }
        if (points != null && points.size() == 1 && finalStatus != null && finalStatus.getTimestamp() != null) {
            return Math.max(0, (int) Duration.between(points.get(0).getSampledAt(), finalStatus.getTimestamp()).toMinutes());
        }
        if (createdAt != null && finalStatus != null && finalStatus.getTimestamp() != null) {
            return Math.max(0, (int) Duration.between(createdAt.atZone(ZoneId.systemDefault()).toOffsetDateTime(), finalStatus.getTimestamp()).toMinutes());
        }
        return 0;
    }

    private Long calculateTotalDistanceMeters(List<TripTrajectoryPointPO> points) {
        if (points == null || points.size() < 2) {
            return 0L;
        }
        long totalDistance = 0L;
        for (int i = 1; i < points.size(); i++) {
            TripTrajectoryPointPO previous = points.get(i - 1);
            TripTrajectoryPointPO current = points.get(i);
            totalDistance += Math.round(haversineMeters(previous.getLat(), previous.getLng(), current.getLat(), current.getLng()));
        }
        return totalDistance;
    }

    private List<Location> sampleTrajectory(List<TripTrajectoryPointPO> points, int maxPoints) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }

        List<TripTrajectoryPointPO> selectedPoints;
        if (points.size() <= maxPoints || maxPoints < 2) {
            selectedPoints = points;
        } else {
            selectedPoints = new ArrayList<>(maxPoints);
            selectedPoints.add(points.get(0));
            int middleTarget = maxPoints - 2;
            int middleSize = points.size() - 2;
            for (int i = 1; i <= middleTarget; i++) {
                int index = (int) Math.round(i * (middleSize + 1.0) / (middleTarget + 1.0));
                index = Math.max(1, Math.min(points.size() - 2, index));
                selectedPoints.add(points.get(index));
            }
            selectedPoints.add(points.get(points.size() - 1));
        }

        List<Location> result = new ArrayList<>(selectedPoints.size());
        for (TripTrajectoryPointPO point : selectedPoints) {
            Location location = new Location();
            location.setLat(point.getLat());
            location.setLng(point.getLng());
            result.add(location);
        }
        return result;
    }

    private List<String> extractPlannedHighlights(ItineraryPlan plan) {
        if (plan == null || plan.getDailyPlans() == null || plan.getDailyPlans().isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> highlights = new LinkedHashSet<>();
        for (DayPlan dayPlan : plan.getDailyPlans()) {
            if (dayPlan == null || dayPlan.getItems() == null) {
                continue;
            }
            for (RouteItem item : dayPlan.getItems()) {
                if (item == null || item.getName() == null || item.getName().isBlank()) {
                    continue;
                }
                highlights.add(item.getName().trim());
                if (highlights.size() >= MAX_PLANNED_HIGHLIGHTS) {
                    return new ArrayList<>(highlights);
                }
            }
        }
        return new ArrayList<>(highlights);
    }

    private List<String> extractItineraryDailyOutline(ItineraryPlan plan) {
        if (plan == null || plan.getDailyPlans() == null || plan.getDailyPlans().isEmpty()) {
            return Collections.emptyList();
        }

        List<String> outline = new ArrayList<>();
        for (DayPlan dayPlan : plan.getDailyPlans()) {
            if (dayPlan == null) {
                continue;
            }
            List<String> itemNames = new ArrayList<>();
            if (dayPlan.getItems() != null) {
                for (RouteItem item : dayPlan.getItems()) {
                    if (item == null || item.getName() == null || item.getName().isBlank()) {
                        continue;
                    }
                    itemNames.add(item.getName().trim());
                    if (itemNames.size() >= 3) {
                        break;
                    }
                }
            }
            if (!itemNames.isEmpty()) {
                outline.add("Day " + (dayPlan.getDayNumber() == null ? "?" : dayPlan.getDayNumber()) + ": "
                        + String.join(" / ", itemNames));
            }
            if (outline.size() >= MAX_DAILY_OUTLINE) {
                break;
            }
        }
        return outline;
    }

    private String buildItineraryRaw(ItineraryPlan plan) {
        if (plan == null) {
            return null;
        }
        try {
            String raw = objectMapper.writeValueAsString(plan);
            if (raw.length() > MAX_ITINERARY_RAW_LENGTH) {
                return raw.substring(0, MAX_ITINERARY_RAW_LENGTH);
            }
            return raw;
        } catch (Exception e) {
            log.warn("Failed to serialize itinerary raw for summary context", e);
            return null;
        }
    }

    private String buildFinalLocationText(UserRealTimeStatus finalStatus) {
        if (finalStatus == null || finalStatus.getLocation() == null) {
            return null;
        }
        Location location = finalStatus.getLocation();
        if (location.getLng() == null || location.getLat() == null) {
            return null;
        }

        StringBuilder text = new StringBuilder();
        if (location.getName() != null && !location.getName().isBlank()) {
            text.append(location.getName().trim()).append(" ");
        }
        text.append("(").append(location.getLat()).append(", ").append(location.getLng()).append(")");
        return text.toString();
    }

    private List<String> buildNearbyPoiHighlights(UserRealTimeStatus finalStatus) {
        if (finalStatus == null || finalStatus.getLocation() == null
                || finalStatus.getLocation().getLng() == null || finalStatus.getLocation().getLat() == null) {
            return Collections.emptyList();
        }

        String location = finalStatus.getLocation().getLng() + "," + finalStatus.getLocation().getLat();
        try {
            JsonNode pois = amapService.searchPoisAround(location, null, null, 2000);
            if (pois == null || !pois.isArray() || pois.isEmpty()) {
                return Collections.emptyList();
            }

            List<String> results = new ArrayList<>();
            for (int i = 0; i < pois.size() && results.size() < MAX_NEARBY_POIS; i++) {
                JsonNode poi = pois.get(i);
                String name = poi.path("name").asText("").trim();
                if (name.isEmpty()) {
                    continue;
                }
                String type = poi.path("type").asText("").trim();
                String distance = poi.path("distance").asText("").trim();

                String typeLabel = type;
                if (typeLabel.contains(";")) {
                    typeLabel = typeLabel.split(";")[0];
                }

                StringBuilder item = new StringBuilder(name);
                List<String> attrs = new ArrayList<>();
                if (!typeLabel.isEmpty()) {
                    attrs.add(typeLabel);
                }
                if (!distance.isEmpty()) {
                    attrs.add(distance + "m");
                }
                if (!attrs.isEmpty()) {
                    item.append("[").append(String.join(" / ", attrs)).append("]");
                }
                results.add(item.toString());
            }
            return results;
        } catch (Exception e) {
            log.warn("Failed to fetch nearby POIs for trip summary: location={}", location, e);
            return Collections.emptyList();
        }
    }

    private double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double radLat1 = Math.toRadians(lat1);
        double radLat2 = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(radLat1) * Math.cos(radLat2)
                * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371000.0 * c;
    }

    private TripSummaryResponse toResponse(TripSummaryPO summaryPO) {
        TripSummaryResponse response = new TripSummaryResponse();
        response.setSummaryId(summaryPO.getSummaryId());
        response.setTripId(summaryPO.getTripId());
        response.setItineraryId(summaryPO.getItineraryId());
        response.setTitle(summaryPO.getTitle());
        response.setSummary(summaryPO.getSummaryText());
        response.setDurationMinutes(summaryPO.getDurationMinutes());
        response.setTotalDistanceMeters(summaryPO.getTotalDistanceMeters());
        response.setTrajectoryPointCount(summaryPO.getTrajectoryPointCount());
        response.setFinalSteps(summaryPO.getFinalSteps());
        response.setFinalCurrentPower(summaryPO.getFinalCurrentPower());
        response.setFinalTotalPower(summaryPO.getFinalTotalPower());
        response.setCreatedAt(summaryPO.getCreatedAt());
        response.setUpdatedAt(summaryPO.getUpdatedAt());
        return response;
    }

    private TripSummaryListItem toListItem(TripSummaryPO summaryPO, boolean includeContent) {
        TripSummaryListItem item = new TripSummaryListItem();
        item.setSummaryId(summaryPO.getSummaryId());
        item.setTripId(summaryPO.getTripId());
        item.setItineraryId(summaryPO.getItineraryId());
        item.setTitle(summaryPO.getTitle());
        item.setSummary(includeContent ? summaryPO.getSummaryText() : null);
        item.setDurationMinutes(summaryPO.getDurationMinutes());
        item.setTotalDistanceMeters(summaryPO.getTotalDistanceMeters());
        item.setTrajectoryPointCount(summaryPO.getTrajectoryPointCount());
        item.setFinalSteps(summaryPO.getFinalSteps());
        item.setFinalCurrentPower(summaryPO.getFinalCurrentPower());
        item.setFinalTotalPower(summaryPO.getFinalTotalPower());
        item.setCreatedAt(summaryPO.getCreatedAt());
        item.setUpdatedAt(summaryPO.getUpdatedAt());
        return item;
    }

    private static class SummarySnapshot {
        private TripSummaryAiRequest aiRequest;
        private Integer durationMinutes;
        private Long totalDistanceMeters;
        private Integer trajectoryPointCount;
        private Integer finalSteps;
        private Integer finalCurrentPower;
        private Integer finalTotalPower;
    }
}