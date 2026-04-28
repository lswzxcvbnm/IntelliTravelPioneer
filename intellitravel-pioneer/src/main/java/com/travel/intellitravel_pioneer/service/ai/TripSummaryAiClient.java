package com.travel.intellitravel_pioneer.service.ai;

import com.travel.intellitravel_pioneer.vo.trip.TripSummaryAiRequest;
import com.travel.intellitravel_pioneer.vo.trip.TripSummaryAiResult;

public interface TripSummaryAiClient {

    TripSummaryAiResult summarizeTrip(String token, TripSummaryAiRequest request);
}