package com.travel.intellitravel_pioneer.repository;

import com.travel.intellitravel_pioneer.po.CapsuleMediaPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CapsuleMediaRepository extends JpaRepository<CapsuleMediaPO, Long> {
    List<CapsuleMediaPO> findByCapsuleId(Long capsuleId);
    void deleteByCapsuleId(Long capsuleId);
}
