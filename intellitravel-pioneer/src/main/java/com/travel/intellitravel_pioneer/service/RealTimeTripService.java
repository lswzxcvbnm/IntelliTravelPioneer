package com.travel.intellitravel_pioneer.service;

import com.travel.intellitravel_pioneer.vo.trip.UserRealTimeStatus;
import com.travel.intellitravel_pioneer.vo.trip.CreateTripResponse;
import com.travel.intellitravel_pioneer.vo.trip.CurrentTripResponse;


public interface RealTimeTripService {

    CreateTripResponse createTrip(String token, String itineraryId, UserRealTimeStatus initialStatus);

    CurrentTripResponse getCurrentTrip(String token);

    String reportTripStatus(String token, String tripId, UserRealTimeStatus status);

    String endTrip(String token, String tripId);

    String getTrajectoryMap(String token, String tripId);
}
