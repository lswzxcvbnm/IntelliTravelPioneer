package com.travel.intellitravel_pioneer.repository;

import com.travel.intellitravel_pioneer.po.UserToolPreferencePO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserToolPreferenceRepository extends JpaRepository<UserToolPreferencePO, Long> {
    Optional<UserToolPreferencePO> findByUserIdAndToolName(String userId, String toolName);
    List<UserToolPreferencePO> findByUserId(String userId);
}
