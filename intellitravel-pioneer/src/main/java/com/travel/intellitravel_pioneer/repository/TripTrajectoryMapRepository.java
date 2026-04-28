package com.travel.intellitravel_pioneer.repository;

import com.travel.intellitravel_pioneer.po.TripTrajectoryMapPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TripTrajectoryMapRepository extends JpaRepository<TripTrajectoryMapPO, Long> {

    TripTrajectoryMapPO findByTripId(String tripId);

    TripTrajectoryMapPO findByTripIdAndUserId(String tripId, String userId);
}
