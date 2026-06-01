package com.travel.intellitravel_pioneer.repository;

import com.travel.intellitravel_pioneer.po.ToolDefinitionPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ToolDefinitionRepository extends JpaRepository<ToolDefinitionPO, Long> {
    Optional<ToolDefinitionPO> findByToolName(String toolName);
    List<ToolDefinitionPO> findByEnabledTrueOrderBySortOrderAsc();
    List<ToolDefinitionPO> findAllByOrderBySortOrderAsc();
}
