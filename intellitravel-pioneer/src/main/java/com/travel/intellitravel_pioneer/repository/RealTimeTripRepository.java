package com.travel.intellitravel_pioneer.repository;

import org.springframework.stereotype.Repository;
import com.travel.intellitravel_pioneer.po.RealTimeTripPO;
import org.springframework.data.jpa.repository.JpaRepository;

@Repository
public interface RealTimeTripRepository extends JpaRepository<RealTimeTripPO, Long> {
    RealTimeTripPO findByTripId(String tripId);

    RealTimeTripPO findFirstByUserIdAndIsCompletedFalseOrderByUpdatedAtDesc(String userId);
}
