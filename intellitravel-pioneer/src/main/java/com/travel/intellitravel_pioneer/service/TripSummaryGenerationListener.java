package com.travel.intellitravel_pioneer.service;

import com.travel.intellitravel_pioneer.po.ItineraryPO;
import com.travel.intellitravel_pioneer.po.TripTrajectoryMapPO;
import com.travel.intellitravel_pioneer.po.UserPO;
import com.travel.intellitravel_pioneer.repository.ItineraryRepository;
import com.travel.intellitravel_pioneer.repository.TripTrajectoryMapRepository;
import com.travel.intellitravel_pioneer.util.TokenUtil;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

@Component
public class TripSummaryGenerationListener {

    private static final Logger log = LoggerFactory.getLogger(TripSummaryGenerationListener.class);

    private final TripSummaryService tripSummaryService;
    private final UserService userService;
    private final ItineraryRepository itineraryRepository;
    private final TripTrajectoryMapRepository tripTrajectoryMapRepository;
    private final TokenUtil tokenUtil;
    private final TripSummaryGenerationStateStore tripSummaryGenerationStateStore;

    public TripSummaryGenerationListener(TripSummaryService tripSummaryService,
                                         UserService userService,
                                         ItineraryRepository itineraryRepository,
                                         TripTrajectoryMapRepository tripTrajectoryMapRepository,
                                         TokenUtil tokenUtil,
                                         TripSummaryGenerationStateStore tripSummaryGenerationStateStore) {
        this.tripSummaryService = tripSummaryService;
        this.userService = userService;
        this.itineraryRepository = itineraryRepository;
        this.tripTrajectoryMapRepository = tripTrajectoryMapRepository;
        this.tokenUtil = tokenUtil;
        this.tripSummaryGenerationStateStore = tripSummaryGenerationStateStore;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTripEnded(TripSummaryGenerationEvent event) {
        tripSummaryGenerationStateStore.markInProgress(event.tripId());
        try {
            tripSummaryService.generateSummary(event.token(), event.tripId(), false);
            tripSummaryGenerationStateStore.markSucceeded(event.tripId());
            log.info("Trip summary generated asynchronously after trip end: tripId={}", event.tripId());
        } catch (Exception e) {
            tripSummaryGenerationStateStore.markFailed(event.tripId(), e.getMessage());
            log.error("Failed to generate trip summary asynchronously after trip end: tripId={}", event.tripId(), e);
        }

        try {
            addExperienceBasedOnTripDays(event.token(), event.tripId());
        } catch (Exception e) {
            log.error("Failed to add experience after trip end: tripId={}", event.tripId(), e);
        }
    }

    private void addExperienceBasedOnTripDays(String token, String tripId) {
        UserPO userPO = tokenUtil.getUser(token);
        if (userPO == null) {
            log.warn("Cannot add experience: user not found for token");
            return;
        }

        TripTrajectoryMapPO tripMap = tripTrajectoryMapRepository.findByTripId(tripId);
        if (tripMap == null || tripMap.getItineraryId() == null || tripMap.getItineraryId().isBlank()) {
            log.warn("Cannot add experience: trip or itinerary not found for tripId={}", tripId);
            return;
        }

        Optional<ItineraryPO> itineraryOpt = itineraryRepository.findById(tripMap.getItineraryId());
        if (itineraryOpt.isEmpty()) {
            log.warn("Cannot add experience: itinerary not found for itineraryId={}", tripMap.getItineraryId());
            return;
        }

        ItineraryPlan plan = itineraryOpt.get().getPlan();
        if (plan == null || plan.getTotalDays() == null || plan.getTotalDays() <= 0) {
            log.warn("Cannot add experience: invalid totalDays in itinerary plan for tripId={}", tripId);
            return;
        }

        int totalDays = plan.getTotalDays();
        int experience = calculateExperienceByDays(totalDays);

        userService.addExperience(userPO.getUsername(), experience);
        log.info("Added {} experience to user {} for {} days trip (tripId={})", 
                experience, userPO.getUsername(), totalDays, tripId);
    }

    private int calculateExperienceByDays(int days) {
        if (days <= 0) {
            return 0;
        }
        if (days == 1) {
            return 100;
        }
        if (days == 2) {
            return 200;
        }
        return 200 + 150 * (days - 2);
    }
}