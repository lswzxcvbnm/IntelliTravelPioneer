package com.travel.intellitravel_pioneer.repository;

import com.travel.intellitravel_pioneer.po.CommunityEventPO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CommunityEventRepository extends JpaRepository<CommunityEventPO, Long> {
    Page<CommunityEventPO> findByReceiverUserIdOrderByCreatedAtDesc(Integer receiverUserId, Pageable pageable);
}
