package com.travel.intellitravel_pioneer.repository;

import com.travel.intellitravel_pioneer.po.TripTrajectoryPointPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TripTrajectoryPointRepository extends JpaRepository<TripTrajectoryPointPO, Long> {

    List<TripTrajectoryPointPO> findByTripIdOrderBySeqAsc(String tripId);

    long countByTripId(String tripId);

    void deleteByTripId(String tripId);
}
