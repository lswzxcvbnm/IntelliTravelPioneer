package com.travel.intellitravel_pioneer.repository;

import com.travel.intellitravel_pioneer.po.ToolCallHistoryPO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ToolCallHistoryRepository extends JpaRepository<ToolCallHistoryPO, Long> {
    Page<ToolCallHistoryPO> findByUserIdOrderByCallTimeDesc(String userId, Pageable pageable);
    Page<ToolCallHistoryPO> findByUserIdAndToolNameOrderByCallTimeDesc(String userId, String toolName, Pageable pageable);
    List<ToolCallHistoryPO> findByUserIdAndItineraryId(String userId, String itineraryId);
    long countByUserIdAndToolName(String userId, String toolName);
    long countByUserIdAndToolNameAndSuccessTrue(String userId, String toolName);
}
