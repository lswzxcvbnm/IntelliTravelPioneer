package com.travel.intellitravel_pioneer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.intellitravel_pioneer.service.ItineraryService;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryPlan;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryRequest;
import com.travel.intellitravel_pioneer.vo.itinerary.ItinerarySaveRequest;
import com.travel.intellitravel_pioneer.vo.itinerary.ItineraryVO;
import com.travel.intellitravel_pioneer.vo.common.Response;
import com.travel.intellitravel_pioneer.vo.trip.GenerationResult;

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
        SseEmitter emitter = new SseEmitter(600000L);
        final java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean(false);

        emitter.onTimeout(() -> {
            if (completed.compareAndSet(false, true)) {
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data("AI 规划耗时过长，已超时。请尝试减少天数或简化需求。"));
                } catch (Exception ignored) {
                }
                emitter.complete();
                System.out.println("====== SSE timeout, connection closed ======");
            }
        });

        emitter.onError(e -> {
            System.err.println("====== SSE Connection Error ======");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.err.println("==================================");
        });

        CompletableFuture.runAsync(() -> {
            try {
                System.out.println("====== Starting generateItinerary SSE ======");
                System.out.println("Request: destination=" + request.getDestination() + ", days=" + request.getDays());
                
                GenerationResult result = itineraryService.generateItineraryWithStats(token, request, message -> {
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
                        System.err.println("Error sending SSE event: " + e.getMessage());
                    }
                });
                
                System.out.println("====== generateItinerary completed, sending result ======");
                String planJson = objectMapper.writeValueAsString(result.getPlan());
                System.out.println("Result JSON length: " + planJson.length());
                emitter.send(SseEmitter.event()
                        .name("result")
                        .data(planJson));

                if (result.getStats() != null) {
                    String statsJson = objectMapper.writeValueAsString(result.getStats());
                    emitter.send(SseEmitter.event()
                            .name("stats")
                            .data(statsJson));
                }
                
                if (completed.compareAndSet(false, true)) {
                    emitter.complete();
                    System.out.println("====== SSE completed successfully ======");
                }
            } catch (Exception e) {
                System.err.println("====== SSE stream error in generateItinerary ======");
                System.err.println("Exception type: " + e.getClass().getName());
                System.err.println("Exception message: " + e.getMessage());
                e.printStackTrace();
                System.err.println("==================================================");
                if (completed.compareAndSet(false, true)) {
                    try {
                        String errorMsg = e.getMessage();
                        if (errorMsg == null || errorMsg.isBlank()) {
                            errorMsg = "AI 规划过程中发生未知错误，请重试";
                        }
                        emitter.send(SseEmitter.event().name("error").data(errorMsg));
                    } catch (Exception ex) {
                        System.err.println("Failed to send error event: " + ex.getMessage());
                    }
                    emitter.complete();
                }
            }
        });
        return emitter;
    }

    @PostMapping(value = "/modify/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter modifyItineraryStream(
            @RequestHeader("token") String token,
            @RequestBody Map<String, Object> body) {
        SseEmitter emitter = new SseEmitter(600000L);
        final java.util.concurrent.atomic.AtomicBoolean completed = new java.util.concurrent.atomic.AtomicBoolean(false);

        emitter.onTimeout(() -> {
            if (completed.compareAndSet(false, true)) {
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data("AI 修改耗时过长，已超时。请重试。"));
                } catch (Exception ignored) {
                }
                emitter.complete();
                System.out.println("====== SSE timeout (modify), connection closed ======");
            }
        });

        emitter.onError(e -> {
            System.err.println("====== SSE Connection Error (modify) ======");
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.err.println("==========================================");
        });

        CompletableFuture.runAsync(() -> {
            try {
                System.out.println("====== Starting modifyItinerary SSE ======");
                
                ItineraryRequest request = objectMapper.convertValue(
                        body.get("request"), ItineraryRequest.class);
                ItineraryPlan currentPlan = objectMapper.convertValue(
                        body.get("currentPlan"), ItineraryPlan.class);
                String userPrompt = (String) body.get("userPrompt");

                GenerationResult result = itineraryService.modifyItineraryWithStats(token, request,
                        currentPlan, userPrompt, message -> {
                    try {
                        if (message.startsWith("PHASE:")) {
                            String phaseBody = message.substring(6);
                            int colonIdx = phaseBody.indexOf(":");
                            if (colonIdx > 0) {
                                emitter.send(SseEmitter.event()
                                        .name("phase")
                                        .data(objectMapper.writeValueAsString(Map.of(
                                            "type", phaseBody.substring(0, colonIdx),
                                            "message", phaseBody.substring(colonIdx + 1)))));
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
                        System.err.println("Error sending SSE event (modify): " + e.getMessage());
                    }
                });
                
                System.out.println("====== modifyItinerary completed, sending result ======");
                String planJson = objectMapper.writeValueAsString(result.getPlan());
                System.out.println("Result JSON length: " + planJson.length());
                emitter.send(SseEmitter.event().name("result").data(planJson));

                if (result.getStats() != null) {
                    String statsJson = objectMapper.writeValueAsString(result.getStats());
                    emitter.send(SseEmitter.event()
                            .name("stats")
                            .data(statsJson));
                }
                
                if (completed.compareAndSet(false, true)) {
                    emitter.complete();
                    System.out.println("====== SSE modify completed successfully ======");
                }
            } catch (Exception e) {
                System.err.println("====== SSE stream error in modifyItinerary ======");
                System.err.println("Exception type: " + e.getClass().getName());
                System.err.println("Exception message: " + e.getMessage());
                e.printStackTrace();
                System.err.println("=================================================");
                if (completed.compareAndSet(false, true)) {
                    try {
                        String errorMsg = e.getMessage();
                        if (errorMsg == null || errorMsg.isBlank()) {
                            errorMsg = "AI 修改过程中发生未知错误，请重试";
                        }
                        emitter.send(SseEmitter.event().name("error").data(errorMsg));
                    } catch (Exception ex) {
                        System.err.println("Failed to send error event: " + ex.getMessage());
                    }
                    emitter.complete();
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
