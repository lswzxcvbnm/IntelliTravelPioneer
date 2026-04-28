package com.travel.intellitravel_pioneer.repository;

import com.travel.intellitravel_pioneer.po.TripSummaryPO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TripSummaryRepository extends JpaRepository<TripSummaryPO, Long> {

    TripSummaryPO findByTripIdAndUserId(String tripId, Integer userId);

    TripSummaryPO findBySummaryId(String summaryId);

    TripSummaryPO findBySummaryIdAndUserId(String summaryId, Integer userId);

    Page<TripSummaryPO> findAllByUserIdOrderByCreatedAtDesc(Integer userId, Pageable pageable);
}