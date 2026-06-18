package com.travel.intellitravel_pioneer.controller;

import com.travel.intellitravel_pioneer.service.RealTimeTripService;
import com.travel.intellitravel_pioneer.vo.trip.CreateTripResponse;
import com.travel.intellitravel_pioneer.vo.common.Response;
import com.travel.intellitravel_pioneer.vo.trip.CurrentTripResponse;
import com.travel.intellitravel_pioneer.vo.trip.TripSummaryListItem;
import com.travel.intellitravel_pioneer.vo.trip.TripSummaryGenerationStatusResponse;
import com.travel.intellitravel_pioneer.vo.trip.TripSummaryResponse;
import com.travel.intellitravel_pioneer.vo.trip.UserRealTimeStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import com.travel.intellitravel_pioneer.util.TokenUtil;

@RestController
@RequestMapping("/api/trips")
public class RealTimeTripController {
    
    private static final Logger logger = LoggerFactory.getLogger(RealTimeTripController.class);

    @Autowired
    private RealTimeTripService realTimeTripService;

    /**
     * 创建新行程
     */
    @PostMapping
    public Response<CreateTripResponse> createTrip(@RequestHeader("token") String token,
                                                   @RequestParam("itinerary_id") String itineraryId,
                                                   @RequestBody UserRealTimeStatus initialStatus) {
        return Response.buildSuccess(realTimeTripService.createTrip(token, itineraryId, initialStatus));
    }

    /**
     * 获取当前进行中的行程
     */
    @GetMapping("/current")
    public Response<CurrentTripResponse> getCurrentTrip(@RequestHeader("token") String token) {
        return Response.buildSuccess(realTimeTripService.getCurrentTrip(token));
    }

    /**
     * 上报实时状态
     */
    @PostMapping("/{trip_id}/status")
    public Response<String> updateTripStatus(
            @RequestHeader("token") String token,
            @PathVariable("trip_id") String tripId,
            @RequestBody UserRealTimeStatus status) {
        return Response.buildSuccess(realTimeTripService.reportTripStatus(token, tripId, status));
    }


    /**
     * 结束行程
     */
    @PostMapping("/{trip_id}/end")
    public Response<String> endTrip(
            @RequestHeader("token") String token,
            @PathVariable("trip_id") String tripId) {
        return Response.buildSuccess(realTimeTripService.endTrip(token, tripId));
    }

    /**
     * 获取行程轨迹图 URL
     */
    @GetMapping("/{trip_id}/trajectory-map")
    public Response<String> getTrajectoryMap(
            @RequestHeader("token") String token,
            @PathVariable("trip_id") String tripId) {
        return Response.buildSuccess(realTimeTripService.getTrajectoryMap(token, tripId));
    }

    @Autowired
    private TokenUtil tokenUtil;

    @Autowired
    private com.travel.intellitravel_pioneer.service.TripSummaryService tripSummaryService;

    /**
     * 为已结束行程生成 AI 总结；默认一个 trip 只保留一条总结。
     */
    @PostMapping("/{trip_id}/summary")
    public Response<TripSummaryResponse> generateTripSummary(
            @RequestHeader("token") String token,
            @PathVariable("trip_id") String tripId,
            @RequestParam(value = "force", defaultValue = "false") boolean force) {
        return Response.buildSuccess(tripSummaryService.generateSummary(token, tripId, force));
    }

    /**
     * 查询 trip 的 AI 总结。
     */
    @GetMapping("/{trip_id}/summary")
    public Response<TripSummaryResponse> getTripSummary(
            @RequestHeader("token") String token,
            @PathVariable("trip_id") String tripId) {
        return Response.buildSuccess(tripSummaryService.getSummary(token, tripId));
    }

    /**
     * 查询 AI 总结异步生成状态。
     */
    @GetMapping("/{trip_id}/summary/status")
    public Response<TripSummaryGenerationStatusResponse> getTripSummaryStatus(
            @RequestHeader("token") String token,
            @PathVariable("trip_id") String tripId) {
        return Response.buildSuccess(tripSummaryService.getSummaryGenerationStatus(token, tripId));
    }

    /**
     * 通过 summary_id 查询 AI 总结（公开可见）。
     */
    @GetMapping("/summaries/{summary_id}")
    public Response<TripSummaryResponse> getTripSummaryBySummaryId(
            @RequestHeader(value = "token", required = false) String token,
            @PathVariable("summary_id") String summaryId) {
        return Response.buildSuccess(tripSummaryService.getSummaryBySummaryId(token, summaryId));
    }

    /**
     * 查询当前用户的历史 trip 总结列表。
     */
    @GetMapping("/summaries")
    public Response<List<TripSummaryListItem>> listTripSummaries(
            @RequestHeader("token") String token,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "itinerary_id", required = false) String itineraryId,
            @RequestParam(value = "include_content", defaultValue = "false") boolean includeContent) {
        try {
            OffsetDateTime fromDt = (from == null ? null : OffsetDateTime.parse(from));
            OffsetDateTime toDt = (to == null ? null : OffsetDateTime.parse(to));
            return Response.buildSuccess(tripSummaryService.listSummaries(token, page, size, fromDt, toDt, itineraryId, includeContent));
        } catch (DateTimeParseException e) {
            return Response.buildFailure("时间格式错误，请使用 ISO_OFFSET_DATE_TIME", "400");
        }
    }
}
