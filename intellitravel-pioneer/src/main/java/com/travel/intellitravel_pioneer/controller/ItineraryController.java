package com.travel.intellitravel_pioneer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.intellitravel_pioneer.service.ItineraryService;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryPlan;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryRequest;
import com.travel.intellitravel_pioneer.vo.itinerary.ItinerarySaveRequest;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryVO;
import com.travel.intellitravel_pioneer.vo.common.Response;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/itinerary")
public class ItineraryController {

    @Autowired
    private ItineraryService itineraryService;

    @PostMapping("/generate")
    public Response<ItineraryPlan> generateItinerary(
            @RequestHeader("token") String token,
            @RequestBody ItineraryRequest request) {
        ItineraryPlan plan = itineraryService.generateItinerary(token, request);
        return Response.buildSuccess(plan);
    }

    @Autowired
    private ObjectMapper objectMapper;

    @PostMapping(value = "/generate/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateItineraryStream(
            @RequestHeader("token") String token,
            @RequestBody ItineraryRequest request) {
        SseEmitter emitter = new SseEmitter(300000L);

        emitter.onTimeout(() -> {
            emitter.complete();
        });

        CompletableFuture.runAsync(() -> {
            try {
                ItineraryPlan plan = itineraryService.generateItinerary(token, request, message -> {
                    try {
                        if (message.startsWith("PHASE:")) {
                            String body = message.substring(6);
                            int colonIdx = body.indexOf(":");
                            if (colonIdx > 0) {
                                String phaseType = body.substring(0, colonIdx);
                                String phaseMsg = body.substring(colonIdx + 1);
                                emitter.send(SseEmitter.event()
                                        .name("phase")
                                        .data(objectMapper.writeValueAsString(
                                                Map.of("type", phaseType, "message", phaseMsg))));
                            }
                        } else if (message.startsWith("THOUGHT:")) {
                            emitter.send(SseEmitter.event()
                                    .name("thought")
                                    .data(message.substring(8)));
                        } else {
                            emitter.send(SseEmitter.event()
                                    .name("tool")
                                    .data(message));
                        }
                    } catch (Exception e) {
                    }
                });
                emitter.send(SseEmitter.event()
                        .name("result")
                        .data(plan));
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                    emitter.completeWithError(e);
                } catch (Exception ex) {
                }
            }
        });
        return emitter;
    }

    @PostMapping("/save")
    public Response<String> saveItinerary(
            @RequestHeader("token") String token,
            @RequestBody ItinerarySaveRequest request) {
        String itineraryId = itineraryService.saveItinerary(token, request);
        return Response.buildSuccess(itineraryId);
    }

    @PutMapping("/update")
    public Response<String> updateItinerary(
            @RequestHeader("token") String token,
            @RequestParam("itineraryId") String itineraryId,
            @RequestBody ItinerarySaveRequest request) {
        itineraryService.updateItinerary(token, itineraryId, request);
        return Response.buildSuccess("itinerary updated successfully");
    }

    @GetMapping("/detail/{itineraryId}")
    public Response<ItineraryVO> getItineraryDetail(
            @RequestHeader("token") String token,
            @PathVariable("itineraryId") String itineraryId) {
        ItineraryVO itinerary = itineraryService.getItineraryDetail(token, itineraryId);
        return Response.buildSuccess(itinerary);
    }

    @GetMapping("getall")
    public Response<List<String>> getAllItineraries(@RequestHeader("token") String token) {
        List<String> itineraryIds = itineraryService.getAllItineraries(token);
        return Response.buildSuccess(itineraryIds);
    }

    @GetMapping("/user/{userId}")
    public Response<List<String>> getItinerariesByUserId(@PathVariable("userId") String userId) {
        List<String> itineraries = itineraryService.getItinerariesByUserId(userId);
        return Response.buildSuccess(itineraries);
    }

    @DeleteMapping("/delete")
    public Response<String> deleteItinerary(@RequestHeader("token") String token,
            @RequestParam("itineraryId") String itineraryId) {
        itineraryService.deleteItinerary(token, itineraryId);
        return Response.buildSuccess(itineraryId);
    }
}