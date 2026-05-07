package com.travel.intellitravel_pioneer.service.serviceImpl;

import com.travel.intellitravel_pioneer.exception.IntellitravelException;
import com.travel.intellitravel_pioneer.po.ItineraryPO;
import com.travel.intellitravel_pioneer.repository.ItineraryRepository;
import com.travel.intellitravel_pioneer.service.ItineraryService;
import com.travel.intellitravel_pioneer.service.ai.TripPlanningAiClient;
import com.travel.intellitravel_pioneer.util.TokenUtil;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryPlan;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryRequest;
import com.travel.intellitravel_pioneer.vo.itinerary.ItinerarySaveRequest;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryVO;
import com.travel.intellitravel_pioneer.vo.trip.GenerationResult;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ItineraryServiceImpl implements ItineraryService {

    @Autowired
    private TripPlanningAiClient tripPlanningAiClient;

    @Autowired
    private ItineraryRepository itineraryRepository;

    @Autowired
    private TokenUtil tokenUtil;

    @Override
    public ItineraryPlan modifyItinerary(String token, ItineraryRequest request,
            ItineraryPlan currentPlan, String userPrompt) {
        GenerationResult result = modifyItineraryWithStats(token, request, currentPlan, userPrompt, null);
        return result != null ? result.getPlan() : null;
    }

    @Override
    public GenerationResult modifyItineraryWithStats(String token, ItineraryRequest request,
            ItineraryPlan currentPlan, String userPrompt,
            java.util.function.Consumer<String> toolNotifier) {
        if (token == null || token.isBlank()) {
            throw new IntellitravelException("token is required");
        }
        if (request == null || currentPlan == null) {
            throw new IntellitravelException("request and currentPlan are required");
        }
        try {
            return tripPlanningAiClient.modifyItineraryWithStats(token, request, currentPlan, userPrompt, toolNotifier);
        } catch (Exception e) {
            throw new IntellitravelException("Failed to modify itinerary: " + e.getMessage());
        }
    }

    @Override
    public ItineraryPlan generateItinerary(String token, ItineraryRequest request) {
        GenerationResult result = generateItineraryWithStats(token, request, null);
        return result != null ? result.getPlan() : null;
    }

    @Override
    public GenerationResult generateItineraryWithStats(String token, ItineraryRequest request,
            java.util.function.Consumer<String> toolNotifier) {
        if (token == null || token.isBlank()) {
            throw new IntellitravelException("token is required");
        }
        if (request == null) {
            throw new IntellitravelException("request body is required");
        }

        try {
            return tripPlanningAiClient.generateItineraryWithStats(token, request, toolNotifier);
        } catch (Exception e) {
            throw new IntellitravelException("Failed to generate itinerary: " + e.getMessage());
        }
    }

    @Override
    public String saveItinerary(String token, ItinerarySaveRequest request) {
        if (token == null || token.isBlank()) {
            throw new IntellitravelException("token is required");
        }
        if (request == null) {
            throw new IntellitravelException("request body is required");
        }

        String userId = String.valueOf(tokenUtil.getUser(token).getId());

        ItineraryPlan plan = ItineraryPlan.builder()
                .title(request.getTitle())
                .estimatedCost(request.getEstimatedCost())
                .totalDays(request.getTotalDays())
                .destination(request.getDestination())
                .dailyPlans(request.getDailyPlans())
                .build();

        ItineraryPO itineraryPO = ItineraryPO.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .title(request.getTitle())
                .plan(plan)
                .stats(request.getStats())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        itineraryRepository.save(itineraryPO);
        return itineraryPO.getId();
    }

    @Override
    public void updateItinerary(String token, String itineraryId, ItinerarySaveRequest request) {
        if (token == null || token.isBlank()) {
            throw new IntellitravelException("token is required");
        }
        if (itineraryId == null || itineraryId.isBlank()) {
            throw new IntellitravelException("itineraryId is required");
        }
        if (request == null) {
            throw new IntellitravelException("plan data is required");
        }

        ItineraryPlan plan = ItineraryPlan.builder()
                .title(request.getTitle())
                .estimatedCost(request.getEstimatedCost())
                .totalDays(request.getTotalDays())
                .destination(request.getDestination())
                .dailyPlans(request.getDailyPlans())
                .build();

        String userId = String.valueOf(tokenUtil.getUser(token).getId());
        ItineraryPO existingItinerary = itineraryRepository.findById(itineraryId)
                .orElseThrow(() -> new IntellitravelException("itinerary not found"));

        if (!existingItinerary.getUserId().equals(userId)) {
            throw new IntellitravelException("access denied: itinerary does not belong to user");
        }

        existingItinerary.setTitle(request.getTitle());
        existingItinerary.setPlan(plan);
        if (request.getStats() != null) {
            existingItinerary.setStats(request.getStats());
        }
        existingItinerary.setUpdateTime(LocalDateTime.now());
        itineraryRepository.save(existingItinerary);
    }

    @Override
    public ItineraryVO getItineraryDetail(String token, String itineraryId) {
        if (token == null || token.isBlank()) {
            throw new IntellitravelException("token is required");
        }
        if (itineraryId == null || itineraryId.isBlank()) {
            throw new IntellitravelException("itineraryId is required");
        }

        ItineraryPO itinerary = itineraryRepository.findById(itineraryId)
                .orElseThrow(() -> new IntellitravelException("itinerary not found"));

        return ItineraryVO.builder()
                .id(itinerary.getId())
                .userId(itinerary.getUserId())
                .title(itinerary.getTitle())
                .destination(itinerary.getPlan() == null ? null : itinerary.getPlan().getDestination())
                .plan(itinerary.getPlan())
                .stats(itinerary.getStats())
                .createTime(itinerary.getCreateTime())
                .updateTime(itinerary.getUpdateTime())
                .build();
    }

    @Override
    public java.util.List<String> getAllItineraries(String token) {
        if (token == null || token.isBlank()) {
            throw new IntellitravelException("token is required");
        }

        String userId = String.valueOf(tokenUtil.getUser(token).getId());
        return itineraryRepository.findByUserId(userId)
                .stream()
                .map(ItineraryPO::getId)
                .toList();
    }

    @Override
    public java.util.List<String> getItinerariesByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IntellitravelException("userId is required");
        }
        return itineraryRepository.findByUserId(userId).stream()
                .map(ItineraryPO::getId)
                .toList();
    }

    @Override
    public void deleteItinerary(String token, String itineraryId) {
        if (token == null || token.isBlank()) {
            throw new IntellitravelException("token is required");
        }
        if (itineraryId == null || itineraryId.isBlank()) {
            throw new IntellitravelException("itineraryId is required");
        }

        String userId = String.valueOf(tokenUtil.getUser(token).getId());
        ItineraryPO existingItinerary = itineraryRepository.findById(itineraryId)
                .orElseThrow(() -> new IntellitravelException("itinerary not found"));

        if (!existingItinerary.getUserId().equals(userId)) {
            throw new IntellitravelException("access denied: itinerary does not belong to user");
        }

        itineraryRepository.deleteById(itineraryId);
    }
}
