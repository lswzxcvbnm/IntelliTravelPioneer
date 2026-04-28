package com.travel.intellitravel_pioneer.controller;

import com.travel.intellitravel_pioneer.service.ItineraryService;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryPlan;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryRequest;
import com.travel.intellitravel_pioneer.vo.itinerary.ItinerarySaveRequest;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryVO;
import com.travel.intellitravel_pioneer.vo.common.Response;

import java.util.List;

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

    @PostMapping(value = "/generate/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter generateItineraryStream(
            @RequestHeader("token") String token,
            @RequestBody ItineraryRequest request) {
        // 5 minutes timeout to ensure slow agents can finish
        SseEmitter emitter = new SseEmitter(300000L);

        emitter.onTimeout(() -> {
            emitter.complete();
        });

        CompletableFuture.runAsync(() -> {
            try {
                ItineraryPlan plan = itineraryService.generateItinerary(token, request, message -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("tool")
                                .data(message));
                    } catch (Exception e) {
                        // If sending fails (e.g. client disconnected), we might want to log it
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
                    // ignore
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