package com.travel.intellitravel_pioneer.repository;

import com.travel.intellitravel_pioneer.po.SkillPO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillRepository extends JpaRepository<SkillPO, String> {
    List<SkillPO> findByUserId(String userId);
    List<SkillPO> findByIsPublicTrue();
}
