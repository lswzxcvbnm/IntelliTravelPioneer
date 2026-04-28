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
import com.travel.intellitravel_pioneer.service.RealTimeTripService;
import com.travel.intellitravel_pioneer.service.TripSummaryGenerationEvent;
import com.travel.intellitravel_pioneer.util.TokenUtil;
import com.travel.intellitravel_pioneer.vo.trip.CreateTripResponse;
import com.travel.intellitravel_pioneer.vo.trip.CurrentTripResponse;
import com.travel.intellitravel_pioneer.vo.trip.Location;
import com.travel.intellitravel_pioneer.vo.trip.PowerDetail;
import com.travel.intellitravel_pioneer.vo.trip.UserRealTimeStatus;
import com.travel.intellitravel_pioneer.vo.user.UserHealthVO;
import jakarta.annotation.PostConstruct;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RealTimeTripServiceImpl implements RealTimeTripService {

    private static final int DEFAULT_HEART_RATE = 90;
    private static final int DEFAULT_INITIAL_POWER = 1000;
    private static final double MIN_SAMPLE_DISTANCE_METERS = 20.0;
    private static final long MIN_SAMPLE_INTERVAL_SECONDS = 30;
    private static final double SIMPLIFY_EPSILON_METERS = 12.0;
    private static final int MAX_TRAJECTORY_POINTS = 2000;
    private static final int TRAJECTORY_BATCH_SIZE = 20;

    private final UserHealthRepository userHealthRepository;
    private final RealTimeTripRepository realTimeTripRepository;
    private final TripTrajectoryMapRepository tripTrajectoryMapRepository;
    private final TripTrajectoryPointRepository tripTrajectoryPointRepository;
    private final AmapService amapService;
    private final TokenUtil tokenUtil;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final Map<String, UserRealTimeStatus> latestStatusByTripId = new ConcurrentHashMap<>();
    private final Map<String, TrajectorySample> latestAcceptedSampleByTripId = new ConcurrentHashMap<>();
    private final Map<String, Integer> nextTrajectorySeqByTripId = new ConcurrentHashMap<>();
    private final Map<String, List<TripTrajectoryPointPO>> trajectoryBufferByTripId = new ConcurrentHashMap<>();
    private final Set<String> endedTripIds = ConcurrentHashMap.newKeySet();

    public RealTimeTripServiceImpl(UserHealthRepository userHealthRepository,
                                   RealTimeTripRepository realTimeTripRepository,
                                   TripTrajectoryMapRepository tripTrajectoryMapRepository,
                                   TripTrajectoryPointRepository tripTrajectoryPointRepository,
                                   AmapService amapService,
                                   TokenUtil tokenUtil,
                                   ApplicationEventPublisher applicationEventPublisher) {
        this.userHealthRepository = userHealthRepository;
        this.realTimeTripRepository = realTimeTripRepository;
        this.tripTrajectoryMapRepository = tripTrajectoryMapRepository;
        this.tripTrajectoryPointRepository = tripTrajectoryPointRepository;
        this.amapService = amapService;
        this.tokenUtil = tokenUtil;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @PostConstruct
    public void warmUpActiveTripStatusCache() {
        List<RealTimeTripPO> persistedTrips = realTimeTripRepository.findAll();
        for (RealTimeTripPO tripPO : persistedTrips) {
            if (tripPO == null || tripPO.getLastStatus() == null) {
                continue;
            }

            UserRealTimeStatus lastStatus = tripPO.getLastStatus();
            String tripId = tripPO.getTripId();
            if (tripId == null || tripId.isBlank()) {
                tripId = lastStatus.getTripId();
            }
            if (tripId == null || tripId.isBlank()) {
                continue;
            }

            if (Boolean.TRUE.equals(tripPO.getIsCompleted())) {
                endedTripIds.add(tripId);
                continue;
            }

            if (tripPO.getItineraryId() == null || tripPO.getItineraryId().isBlank()) {
                continue;
            }

            latestStatusByTripId.put(tripId, lastStatus);
            TrajectorySample sample = toTrajectorySample(lastStatus);
            if (sample != null) {
                latestAcceptedSampleByTripId.put(tripId, sample);
            }
        }
    }

    @Override
    @Transactional
    public CreateTripResponse createTrip(String token, String itineraryId, UserRealTimeStatus initialStatus) {
        validateCreateTripRequest(token, itineraryId, initialStatus);

        UserPO userPO = resolveUserByToken(token);
        ensureNoActiveTripBeforeCreate(userPO);
        UserHealthVO userHealthVO = getOrCreateUserHealthVO(userPO);
        int combatPower = userHealthVO.getCombatPower() == null ? DEFAULT_INITIAL_POWER : userHealthVO.getCombatPower();

        UserRealTimeStatus normalizedInitialStatus = buildInitialStatus(initialStatus, combatPower);

        String tripId = UUID.randomUUID().toString().replace("-", "");
        normalizedInitialStatus.setTripId(tripId);
        endedTripIds.remove(tripId);
        latestStatusByTripId.put(tripId, normalizedInitialStatus);
        initializeTrajectory(tripId, String.valueOf(userPO.getId()), normalizedInitialStatus);
        initializeTrajectoryMap(tripId, String.valueOf(userPO.getId()), itineraryId);
        createRealTimeTrip(userPO, tripId, itineraryId, normalizedInitialStatus);

        CreateTripResponse response = new CreateTripResponse();
        response.setTripId(tripId);
        response.setMessage("行程创建成功");
        return response;
    }

    private void ensureNoActiveTripBeforeCreate(UserPO userPO) {
        String userId = String.valueOf(userPO.getId());
        RealTimeTripPO tripPO = realTimeTripRepository.findFirstByUserIdAndIsCompletedFalseOrderByUpdatedAtDesc(userId);
        if (tripPO == null) {
            return;
        }

        String tripId = tripPO.getTripId();
        if (tripId == null || tripId.isBlank()) {
            clearActiveTripRecord(tripPO, tripPO.getLastStatus());
            return;
        }

        String itineraryId = tripPO.getItineraryId();
        UserRealTimeStatus lastStatus = tripPO.getLastStatus();

        boolean completedInRuntime = tripId != null && endedTripIds.contains(tripId);
        boolean completedByColumn = Boolean.TRUE.equals(tripPO.getIsCompleted());
        if (completedInRuntime || completedByColumn) {
            clearActiveTripRecord(tripPO, lastStatus);
            return;
        }

        // Incomplete or mismatched persisted state is treated as stale and cleaned up.
        if (itineraryId == null || itineraryId.isBlank()) {
            clearActiveTripRecord(tripPO, lastStatus);
            return;
        }

        throw new IntellitravelException("存在未结束行程，请先结束后再创建");
    }

    @Override
    @Transactional
    public CurrentTripResponse getCurrentTrip(String token) {
        validateToken(token);
        UserPO userPO = resolveUserByToken(token);

        CurrentTripResponse response = new CurrentTripResponse();
        response.setHasActiveTrip(false);
        response.setTripId(null);
        response.setItineraryId(null);

        RealTimeTripPO tripPO = realTimeTripRepository
                .findFirstByUserIdAndIsCompletedFalseOrderByUpdatedAtDesc(String.valueOf(userPO.getId()));
        if (tripPO == null) {
            return response;
        }

        String activeTripId = tripPO.getTripId();
        String itineraryId = tripPO.getItineraryId();
        UserRealTimeStatus lastStatus = tripPO.getLastStatus();

        if (itineraryId == null || itineraryId.isBlank() || activeTripId == null || activeTripId.isBlank()) {
            clearActiveTripRecord(tripPO, lastStatus);
            return response;
        }

        if (endedTripIds.contains(activeTripId)) {
            RealTimeTripPO persistedTrip = realTimeTripRepository.findByTripId(activeTripId);
            if (persistedTrip == null || Boolean.TRUE.equals(persistedTrip.getIsCompleted())) {
                if (persistedTrip != null) {
                    clearActiveTripRecord(persistedTrip, persistedTrip.getLastStatus());
                }
                return response;
            }
            // Runtime marker can be stale after restart/recovery, keep DB as source of truth.
            endedTripIds.remove(activeTripId);
        }

        response.setHasActiveTrip(true);
        response.setTripId(activeTripId);
        response.setItineraryId(itineraryId);
        response.setCreatedAt(tripPO.getCreatedAt());
        return response;
    }

    private void createRealTimeTrip(UserPO userPO, String tripId, String itineraryId, UserRealTimeStatus initialStatus) {
        RealTimeTripPO tripPO = new RealTimeTripPO();
        tripPO.setTripId(tripId);
        tripPO.setUserId(String.valueOf(userPO.getId()));
        tripPO.setItineraryId(itineraryId);
        tripPO.setIsCompleted(false);
        tripPO.setLastStatus(initialStatus);
        realTimeTripRepository.save(tripPO);
    }

    private RealTimeTripPO updateOngoingTripStatus(String userId, String tripId, UserRealTimeStatus status) {
        RealTimeTripPO tripPO = resolveTripRecordForUser(userId, tripId);
        tripPO.setLastStatus(status);
        tripPO.setIsCompleted(false);
        return realTimeTripRepository.save(tripPO);
    }

    @Override
    @Transactional
    public String reportTripStatus(String token, String tripId, UserRealTimeStatus status) {
        validateStatusRequest(token, tripId, status);

        UserPO userPO = resolveUserByToken(token);
        validateTripOwnership(tripId, String.valueOf(userPO.getId()));

        if (endedTripIds.contains(tripId)) {
            throw new IntellitravelException("trip already ended");
        }

        UserRealTimeStatus previousStatus = resolveLatestStatusWithCacheFallback(userPO, tripId);
        if (previousStatus == null) {
            throw new IntellitravelException("trip not found");
        }
        RealTimeTripPO persistedTrip = resolveTripRecordForUser(String.valueOf(userPO.getId()), tripId);
        if (Boolean.TRUE.equals(persistedTrip.getIsCompleted())) {
            endedTripIds.add(tripId);
            throw new IntellitravelException("trip already ended");
        }
        enrichPowerMetrics(previousStatus, status);
        status.setTripId(tripId);
        latestStatusByTripId.put(tripId, status);
        
        boolean forceFlush = Boolean.TRUE.equals(status.getFlushBuffer());
        appendTrajectorySample(tripId, String.valueOf(userPO.getId()), status, forceFlush);
        updateOngoingTripStatus(String.valueOf(userPO.getId()), tripId, status);

        return "状态上报成功";
    }

    
    @Override
    @Transactional
    public String endTrip(String token, String tripId) {
        validateTripIdRequest(token, tripId);
        UserPO userPO = resolveUserByToken(token);
        validateTripOwnership(tripId, String.valueOf(userPO.getId()));

        if (endedTripIds.contains(tripId)) {
            return "行程已结束";
        }
        UserRealTimeStatus latestStatus = resolveLatestStatusForEndTrip(userPO, tripId);
        RealTimeTripPO tripPO = resolveTripRecordForUser(String.valueOf(userPO.getId()), tripId);
        clearActiveTripRecord(tripPO, latestStatus);
        endedTripIds.add(tripId);
        publishTripSummaryGenerationEvent(token, tripId);
        return buildTripEndMessage(tripId, "行程已结束");
    }

    private void publishTripSummaryGenerationEvent(String token, String tripId) {
        applicationEventPublisher.publishEvent(new TripSummaryGenerationEvent(token, tripId));
    }

    private UserRealTimeStatus resolveLatestStatusForEndTrip(UserPO userPO, String tripId) {
        UserRealTimeStatus latestStatus = resolveLatestStatusWithCacheFallback(userPO, tripId);
        if (latestStatus == null) {
            throw new IntellitravelException("trip not found");
        }
        return latestStatus;
    }

    private UserRealTimeStatus resolveLatestStatusWithCacheFallback(UserPO userPO, String tripId) {
        UserRealTimeStatus cachedStatus = latestStatusByTripId.get(tripId);
        if (cachedStatus != null) {
            return cachedStatus;
        }

        RealTimeTripPO tripPO = realTimeTripRepository.findByTripId(tripId);
        if (tripPO == null || tripPO.getLastStatus() == null) {
            return null;
        }

        if (!String.valueOf(userPO.getId()).equals(tripPO.getUserId())) {
            return null;
        }

        UserRealTimeStatus persistedStatus = tripPO.getLastStatus();
        if (persistedStatus.getTripId() == null || persistedStatus.getTripId().isBlank()) {
            persistedStatus.setTripId(tripId);
        }
        if (Boolean.TRUE.equals(tripPO.getIsCompleted())) {
            endedTripIds.add(tripId);
        }

        latestStatusByTripId.put(tripId, persistedStatus);
        TrajectorySample sample = toTrajectorySample(persistedStatus);
        if (sample != null) {
            latestAcceptedSampleByTripId.put(tripId, sample);
        }
        return persistedStatus;
    }

    @Override
    public String getTrajectoryMap(String token, String tripId) {
        validateTripIdRequest(token, tripId);
        UserPO userPO = resolveUserByToken(token);
        TripTrajectoryMapPO mapPO = tripTrajectoryMapRepository.findByTripIdAndUserId(tripId, String.valueOf(userPO.getId()));
        if (mapPO == null) {
            throw new IntellitravelException("trip not found");
        }
        if (mapPO.getTrajectoryMapUrl() == null || mapPO.getTrajectoryMapUrl().isBlank()) {
            throw new IntellitravelException("trajectory map not generated");
        }
        return mapPO.getTrajectoryMapUrl();
    }

    private void validateTripIdRequest(String token, String tripId) {
        validateToken(token);
        if (tripId == null || tripId.isBlank()) {
            throw new IntellitravelException("trip_id is required");
        }
    }

    private void validateStatusRequest(String token, String tripId, UserRealTimeStatus status) {
        validateTripIdRequest(token, tripId);
        validateStatusBody(status);
    }

    private void validateCreateTripRequest(String token, String itineraryId, UserRealTimeStatus initialStatus) {
        validateToken(token);
        if (itineraryId == null || itineraryId.isBlank()) {
            throw new IntellitravelException("itinerary_id is required");
        }
        if (initialStatus == null) {
            throw new IntellitravelException("initial status body is required");
        }
        if (initialStatus.getLocation() == null
                || initialStatus.getLocation().getLat() == null
                || initialStatus.getLocation().getLng() == null) {
            throw new IntellitravelException("location.lat and location.lng are required");
        }
    }

    private UserPO resolveUserByToken(String token) {
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

    private UserHealthVO getOrCreateUserHealthVO(UserPO userPO) {
        UserHealthPO userHealthPO = userHealthRepository.findByUserId(userPO.getId());
        if (userHealthPO != null) {
            return userHealthPO.toVO();
        }

        UserHealthPO newHealthPO = new UserHealthPO();
        newHealthPO.setUserId(userPO.getId());
        newHealthPO.setUsername(userPO.getUsername());
        newHealthPO.setCombatPower(DEFAULT_INITIAL_POWER);
        return userHealthRepository.save(newHealthPO).toVO();
    }

    private UserRealTimeStatus buildInitialStatus(UserRealTimeStatus initialStatus, int combatPower) {
        UserRealTimeStatus status = initialStatus;

        if (status.getTimestamp() == null) {
            status.setTimestamp(OffsetDateTime.now());
        }
        if (status.getHeartRate() == null || status.getHeartRate() <= 0) {
            status.setHeartRate(DEFAULT_HEART_RATE);
        }
        if (status.getSteps() == null || status.getSteps() < 0) {
            status.setSteps(0);
        }

        Location location = status.getLocation();
        if (location == null) {
            location = new Location();
            status.setLocation(location);
        }
        if (location.getLat() == null) {
            location.setLat(0.0);
        }
        if (location.getLng() == null) {
            location.setLng(0.0);
        }

        status.setPowerCost(0);
        status.setTotalPower(combatPower);
        status.setCurrentPower(combatPower);
        if (status.getExtInfo() == null || status.getExtInfo().isBlank()) {
            status.setExtInfo("trip initialized");
        }

        return status;
    }

    private void validateToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IntellitravelException("token is required");
        }
    }

    private void validateStatusBody(UserRealTimeStatus status) {
        if (status == null) {
            throw new IntellitravelException("status body is required");
        }
        if (status.getTimestamp() == null) {
            throw new IntellitravelException("timestamp is required");
        }
        if (status.getHeartRate() == null || status.getHeartRate() <= 0) {
            throw new IntellitravelException("heart_rate must be positive");
        }
        if (status.getSteps() == null || status.getSteps() < 0) {
            throw new IntellitravelException("steps must be greater than or equal to 0");
        }
        if (status.getLocation() == null || status.getLocation().getLat() == null || status.getLocation().getLng() == null) {
            throw new IntellitravelException("location.lat and location.lng are required");
        }
    }

    /**
     * 根据当前和之前的状态计算体力消耗和剩余体力，更新当前状态中的powerCost和currentPower字段，并生成PowerDetail对象包含详细的计算信息
     * 计算逻辑示例：每100步消耗8点体力，心率超过90时每增加1点心率消耗1.8点体力，疲劳指数每增加1点消耗0.4点体力
     * 疲劳指数计算示例：基于平均心率和步数增量综合计算一个0-100的疲劳指数，心率越高、步数越多疲劳指数越高
     * 
     * 注意：如果前端标记了 useFrontendPowerCalculation=true，则跳过后端计算，使用前端传来的值
     */
    private void enrichPowerMetrics(UserRealTimeStatus previousStatus, UserRealTimeStatus currentStatus) {
        if (Boolean.TRUE.equals(currentStatus.getUseFrontendPowerCalculation())) {
            return;
        }

        int stepDelta = calculateStepDelta(previousStatus, currentStatus);

        int estimatedHeartRateAvg = previousStatus == null
                ? currentStatus.getHeartRate()
                : (previousStatus.getHeartRate() + currentStatus.getHeartRate()) / 2;
        int estimatedHeartRateMax = previousStatus == null
                ? currentStatus.getHeartRate()
                : Math.max(previousStatus.getHeartRate(), currentStatus.getHeartRate());

        int fatigueIndex = calculateFatigueIndex(stepDelta, estimatedHeartRateAvg);
        int powerCost = calculatePowerCost(stepDelta, estimatedHeartRateAvg, fatigueIndex);

        int totalPower = previousStatus != null && previousStatus.getTotalPower() != null
            ? previousStatus.getTotalPower()
            : currentStatus.getTotalPower() != null ? currentStatus.getTotalPower() : DEFAULT_INITIAL_POWER;

        int basePower = previousStatus != null && previousStatus.getCurrentPower() != null
                ? previousStatus.getCurrentPower()
                : currentStatus.getCurrentPower() != null ? currentStatus.getCurrentPower() : DEFAULT_INITIAL_POWER;
        int currentPower = Math.max(0, Math.min(totalPower, basePower - powerCost));

        PowerDetail powerDetail = new PowerDetail();
        powerDetail.setWalkingDistance((double) stepDelta * 0.75);
        powerDetail.setEstimatedHeartRateAvg(estimatedHeartRateAvg);
        powerDetail.setEstimatedHeartRateMax(estimatedHeartRateMax);
        powerDetail.setFatigueIndex(fatigueIndex);

        currentStatus.setPowerCost(powerCost);
        currentStatus.setTotalPower(totalPower);
        currentStatus.setCurrentPower(currentPower);
        currentStatus.setPowerDetail(powerDetail);
    }

    private int calculateStepDelta(UserRealTimeStatus previousStatus, UserRealTimeStatus currentStatus) {
        if (previousStatus == null || previousStatus.getSteps() == null) {
            return 0;
        }
        return Math.max(0, currentStatus.getSteps() - previousStatus.getSteps());
    }

    private int calculateFatigueIndex(int stepDelta, int heartRateAvg) {
        double raw = (heartRateAvg - 80) * 0.7 + stepDelta / 120.0;
        int fatigue = (int) Math.round(raw);
        return Math.max(0, Math.min(100, fatigue));
    }

    private int calculatePowerCost(int stepDelta, int heartRateAvg, int fatigueIndex) {
        double cost = stepDelta * 0.08
                + Math.max(0, heartRateAvg - 90) * 1.8
                + fatigueIndex * 0.4;
        return Math.max(0, (int) Math.round(cost));
    }

    /**
     * 内部类：轨迹样本对象，包含经纬度和时间戳，用于轨迹处理和简化算法
     */
    private static class TrajectorySample {
        private final Double lng;
        private final Double lat;
        private final OffsetDateTime timestamp;

        private TrajectorySample(Double lng, Double lat, OffsetDateTime timestamp) {
            this.lng = lng;
            this.lat = lat;
            this.timestamp = timestamp;
        }
    }

    /**
     * 初始化行程轨迹缓存，记录第一条轨迹点
     */
    private void initializeTrajectory(String tripId, String userId, UserRealTimeStatus initialStatus) {
        nextTrajectorySeqByTripId.put(tripId, 0);
        trajectoryBufferByTripId.remove(tripId);
        TrajectorySample initialSample = toTrajectorySample(initialStatus);
        if (initialSample == null) {
            return;
        }
        latestAcceptedSampleByTripId.put(tripId, initialSample);
        addTrajectoryPointToBuffer(tripId, userId, initialSample, true);
    }

    /**
     * 根据最新状态追加轨迹点
     * @param forceFlush 是否强制刷新缓冲区（用于退出导航时确保轨迹点写入数据库）
     */
    private void appendTrajectorySample(String tripId, String userId, UserRealTimeStatus status, boolean forceFlush) {
        TrajectorySample currentSample = toTrajectorySample(status);
        if (currentSample == null) {
            if (forceFlush) {
                flushTrajectoryBuffer(tripId);
            }
            return;
        }
        TrajectorySample previousSample = latestAcceptedSampleByTripId.get(tripId);
        boolean shouldAdd = forceFlush || shouldSample(previousSample, currentSample);
        if (shouldAdd) {
            latestAcceptedSampleByTripId.put(tripId, currentSample);
            addTrajectoryPointToBuffer(tripId, userId, currentSample, forceFlush);
        } else if (forceFlush) {
            flushTrajectoryBuffer(tripId);
        }
    }

    /**
     * 将轨迹点添加到缓存中，如果达到批量大小则批量写入数据库
     */
    private void addTrajectoryPointToBuffer(String tripId, String userId, TrajectorySample sample, boolean forceFlush) {
        TripTrajectoryPointPO pointPO = new TripTrajectoryPointPO();
        pointPO.setTripId(tripId);
        pointPO.setUserId(userId);
        pointPO.setSeq(nextTrajectorySeq(tripId));
        pointPO.setLng(sample.lng);
        pointPO.setLat(sample.lat);
        pointPO.setSampledAt(sample.timestamp);

        List<TripTrajectoryPointPO> buffer = trajectoryBufferByTripId.computeIfAbsent(tripId, key -> new ArrayList<>());
        buffer.add(pointPO);
        if (forceFlush || buffer.size() >= TRAJECTORY_BATCH_SIZE) {
            flushTrajectoryBuffer(tripId);
        }
    }

    /**
     * 获取下一个轨迹点序列号，基于当前缓存和数据库中已有的点数计算
     */
    private int nextTrajectorySeq(String tripId) {
        Integer nextSeq = nextTrajectorySeqByTripId.get(tripId);
        if (nextSeq == null) {
            nextSeq = (int) tripTrajectoryPointRepository.countByTripId(tripId);
        }
        nextTrajectorySeqByTripId.put(tripId, nextSeq + 1);
        return nextSeq;
    }

    /**
     * 将缓存中的轨迹点批量写入数据库，并清空缓存
     */
    private void flushTrajectoryBuffer(String tripId) {
        List<TripTrajectoryPointPO> buffer = trajectoryBufferByTripId.get(tripId);
        if (buffer == null || buffer.isEmpty()) {
            return;
        }
        tripTrajectoryPointRepository.saveAll(new ArrayList<>(buffer));
        buffer.clear();
    }

    /**
     * 行程结束时调用，处理轨迹数据并生成地图URL，如果轨迹数据不足则直接返回结束消息
     */
    private String buildTripEndMessage(String tripId, String baseMessage) {
        String trajectoryMapUrl = finalizeTrajectoryAndBuildMapUrl(tripId);
        if (trajectoryMapUrl == null || trajectoryMapUrl.isBlank()) {
            clearTripRuntimeCache(tripId);
            return baseMessage;
        }
        clearTripRuntimeCache(tripId);
        return baseMessage + "，轨迹图：" + trajectoryMapUrl;
    }

    /**
     * 处理行程轨迹数据：先将缓存中的点写入数据库，然后读取全部轨迹点进行简化和限制，最后生成地图URL
     */
    private String finalizeTrajectoryAndBuildMapUrl(String tripId) {
        flushTrajectoryBuffer(tripId);

        List<TripTrajectoryPointPO> rawPoints = tripTrajectoryPointRepository.findByTripIdOrderBySeqAsc(tripId);
        if (rawPoints.isEmpty()) {
            persistTrajectoryMapUrl(tripId, null);
            return null;
        }

        List<TrajectorySample> samples = new ArrayList<>(rawPoints.size());
        for (TripTrajectoryPointPO pointPO : rawPoints) {
            samples.add(new TrajectorySample(pointPO.getLng(), pointPO.getLat(), pointPO.getSampledAt()));
        }

        List<TrajectorySample> simplified = simplifyTrajectory(samples, SIMPLIFY_EPSILON_METERS);
        List<TrajectorySample> limited = limitTrajectoryPoints(simplified, MAX_TRAJECTORY_POINTS);
        if (limited.size() < 2) {
            limited = limitTrajectoryPoints(samples, Math.min(MAX_TRAJECTORY_POINTS, samples.size()));
        }

        String userId = rawPoints.get(0).getUserId();
        replaceTrajectoryPoints(tripId, userId, limited);

        List<Location> locations = new ArrayList<>(limited.size());
        for (TrajectorySample sample : limited) {
            Location location = new Location();
            location.setLng(sample.lng);
            location.setLat(sample.lat);
            locations.add(location);
        }
        String trajectoryMapUrl = amapService.generateTrajectoryMapUrl(locations);
        persistTrajectoryMapUrl(tripId, trajectoryMapUrl);
        return trajectoryMapUrl;
    }

    private void initializeTrajectoryMap(String tripId, String userId, String itineraryId) {
        TripTrajectoryMapPO mapPO = tripTrajectoryMapRepository.findByTripId(tripId);
        if (mapPO == null) {
            mapPO = new TripTrajectoryMapPO();
            mapPO.setTripId(tripId);
        }
        mapPO.setUserId(userId);
        mapPO.setItineraryId(itineraryId);
        mapPO.setTrajectoryMapUrl(null);
        tripTrajectoryMapRepository.save(mapPO);
    }

    private void persistTrajectoryMapUrl(String tripId, String trajectoryMapUrl) {
        TripTrajectoryMapPO mapPO = tripTrajectoryMapRepository.findByTripId(tripId);
        if (mapPO == null) {
            return;
        }
        mapPO.setTrajectoryMapUrl(trajectoryMapUrl);
        tripTrajectoryMapRepository.save(mapPO);
    }

    private void validateTripOwnership(String tripId, String userId) {
        TripTrajectoryMapPO mapPO = tripTrajectoryMapRepository.findByTripIdAndUserId(tripId, userId);
        if (mapPO == null) {
            throw new IntellitravelException("trip not found");
        }
    }

    private void clearActiveTripRecord(RealTimeTripPO tripPO, UserRealTimeStatus latestStatus) {
        tripPO.setItineraryId(null);
        tripPO.setIsCompleted(true);
        if (latestStatus != null) {
            if (latestStatus.getTripId() == null || latestStatus.getTripId().isBlank()) {
                latestStatus.setTripId(tripPO.getTripId());
            }
            tripPO.setLastStatus(latestStatus);
        }
        realTimeTripRepository.save(tripPO);
    }

    private RealTimeTripPO resolveTripRecordForUser(String userId, String tripId) {
        RealTimeTripPO tripPO = realTimeTripRepository.findByTripId(tripId);
        if (tripPO == null || !userId.equals(tripPO.getUserId())) {
            throw new IntellitravelException("trip not found");
        }
        return tripPO;
    }

    /**
     * 替换行程轨迹点：先删除数据库中原有的轨迹点，再批量插入新的轨迹点，并更新缓存中的序列号和最新接受的样本
     * @param tripId
     * @param userId
     * @param samples
     */
    private void replaceTrajectoryPoints(String tripId, String userId, List<TrajectorySample> samples) {
        tripTrajectoryPointRepository.deleteByTripId(tripId);
        if (samples == null || samples.isEmpty()) {
            return;
        }
        List<TripTrajectoryPointPO> entities = new ArrayList<>(samples.size());
        for (int i = 0; i < samples.size(); i++) {
            TrajectorySample sample = samples.get(i);
            TripTrajectoryPointPO pointPO = new TripTrajectoryPointPO();
            pointPO.setTripId(tripId);
            pointPO.setUserId(userId);
            pointPO.setSeq(i);
            pointPO.setLng(sample.lng);
            pointPO.setLat(sample.lat);
            pointPO.setSampledAt(sample.timestamp);
            entities.add(pointPO);
        }
        tripTrajectoryPointRepository.saveAll(entities);
        nextTrajectorySeqByTripId.put(tripId, entities.size());
        latestAcceptedSampleByTripId.put(tripId, samples.get(samples.size() - 1));
    }

    /**
     * 将用户状态转换为轨迹样本，如果位置数据不完整则返回null
     */
    private TrajectorySample toTrajectorySample(UserRealTimeStatus status) {
        if (status == null || status.getLocation() == null) {
            return null;
        }
        Location location = status.getLocation();
        if (location.getLng() == null || location.getLat() == null) {
            return null;
        }
        OffsetDateTime timestamp = status.getTimestamp() != null ? status.getTimestamp() : OffsetDateTime.now();
        return new TrajectorySample(location.getLng(), location.getLat(), timestamp);
    }

    /**
     * 判断是否应该接受当前轨迹样本，基于与上一个接受的样本的距离和时间间隔
     */
    private boolean shouldSample(TrajectorySample previous, TrajectorySample current) {
        if (current == null) {
            return false;
        }
        if (previous == null) {
            return true;
        }
        long seconds = Math.abs(current.timestamp.toEpochSecond() - previous.timestamp.toEpochSecond());
        double distance = haversineMeters(previous.lat, previous.lng, current.lat, current.lng);
        return distance > MIN_SAMPLE_DISTANCE_METERS || seconds > MIN_SAMPLE_INTERVAL_SECONDS;
    }

    /**
     * 使用Douglas-Peucker算法简化轨迹，保留关键点以保持轨迹形状，同时去除冗余点
     */
    private List<TrajectorySample> simplifyTrajectory(List<TrajectorySample> points, double epsilonMeters) {
        if (points == null || points.size() <= 2) {
            return points == null ? Collections.emptyList() : points;
        }
        boolean[] keep = new boolean[points.size()];
        keep[0] = true;
        keep[points.size() - 1] = true;
        douglasPeucker(points, 0, points.size() - 1, epsilonMeters, keep);
        List<TrajectorySample> result = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            if (keep[i]) {
                result.add(points.get(i));
            }
        }
        return result;
    }

    // Douglas-Peucker算法核心递归函数，标记需要保留的点
    private void douglasPeucker(List<TrajectorySample> points, int start, int end, double epsilonMeters, boolean[] keep) {
        if (end <= start + 1) {
            return;
        }
        double maxDistance = -1;
        int index = -1;
        for (int i = start + 1; i < end; i++) {
            double distance = perpendicularDistanceMeters(points.get(i), points.get(start), points.get(end));
            if (distance > maxDistance) {
                maxDistance = distance;
                index = i;
            }
        }
        if (index > 0 && maxDistance > epsilonMeters) {
            keep[index] = true;
            douglasPeucker(points, start, index, epsilonMeters, keep);
            douglasPeucker(points, index, end, epsilonMeters, keep);
        }
    }

    /**
     * 限制轨迹点数量，如果超过最大值则等距抽取关键点，保证首尾点一定被保留
     * @param points
     * @param maxPoints
     * @return
     */
    private List<TrajectorySample> limitTrajectoryPoints(List<TrajectorySample> points, int maxPoints) {
        if (points == null || points.size() <= maxPoints || maxPoints < 2) {
            return points == null ? Collections.emptyList() : points;
        }
        List<TrajectorySample> result = new ArrayList<>(maxPoints);
        result.add(points.get(0));
        int middleTarget = maxPoints - 2;
        int middleSize = points.size() - 2;
        for (int i = 1; i <= middleTarget; i++) {
            int index = (int) Math.round(i * (middleSize + 1.0) / (middleTarget + 1.0));
            index = Math.max(1, Math.min(points.size() - 2, index));
            result.add(points.get(index));
        }
        result.add(points.get(points.size() - 1));
        return result;
    }

    //Douglas-Peucker算法辅助函数
    private double perpendicularDistanceMeters(TrajectorySample point, TrajectorySample start, TrajectorySample end) {
        if (start.lng.equals(end.lng) && start.lat.equals(end.lat)) {
            return haversineMeters(point.lat, point.lng, start.lat, start.lng);
        }

        double baseLatRad = Math.toRadians((start.lat + end.lat) / 2.0);
        double meterPerDegLat = 111320.0;
        double meterPerDegLng = 111320.0 * Math.cos(baseLatRad);

        double sx = start.lng * meterPerDegLng;
        double sy = start.lat * meterPerDegLat;
        double ex = end.lng * meterPerDegLng;
        double ey = end.lat * meterPerDegLat;
        double px = point.lng * meterPerDegLng;
        double py = point.lat * meterPerDegLat;

        double dx = ex - sx;
        double dy = ey - sy;
        double t = ((px - sx) * dx + (py - sy) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));

        double projX = sx + t * dx;
        double projY = sy + t * dy;
        double distX = px - projX;
        double distY = py - projY;
        return Math.sqrt(distX * distX + distY * distY);
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

    private void clearTripRuntimeCache(String tripId) {
        latestAcceptedSampleByTripId.remove(tripId);
        nextTrajectorySeqByTripId.remove(tripId);
        trajectoryBufferByTripId.remove(tripId);
        latestStatusByTripId.remove(tripId);
    }
}
