package com.travel.intellitravel_pioneer.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import com.travel.intellitravel_pioneer.po.UserLongTermMemoryPO;



public interface UserLongTermMemoryRepository extends JpaRepository<UserLongTermMemoryPO, Long> {
    List<UserLongTermMemoryPO> findByUserIdAndMemoryType(String userId, String memoryType);
    List<UserLongTermMemoryPO> findByUserId(String userId);
}