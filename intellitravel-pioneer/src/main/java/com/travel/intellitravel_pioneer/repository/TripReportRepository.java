package com.travel.intellitravel_pioneer.repository;

import com.travel.intellitravel_pioneer.po.TripReportPO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TripReportRepository extends JpaRepository<TripReportPO, Long> {
    TripReportPO findByTripId(String tripId);

    // 支持按 tripId + userId 分页查询，按创建时间倒序
    Page<TripReportPO> findAllByTripIdAndUserIdOrderByCreatedAtDesc(String tripId, Integer userId, Pageable pageable);
}
