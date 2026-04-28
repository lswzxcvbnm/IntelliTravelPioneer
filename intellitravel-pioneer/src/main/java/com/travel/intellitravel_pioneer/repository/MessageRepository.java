package com.travel.intellitravel_pioneer.repository;

import com.travel.intellitravel_pioneer.po.MessagePO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface MessageRepository extends JpaRepository<MessagePO, Integer> {

    Page<MessagePO> findBySenderIdAndReceiverIdOrderByCreatedAtAsc(Integer senderId, Integer receiverId, Pageable pageable);

    Page<MessagePO> findByReceiverIdAndSenderIdOrderByCreatedAtAsc(Integer receiverId, Integer senderId, Pageable pageable);

    @Query("SELECT m FROM MessagePO m WHERE (m.senderId = :user1 AND m.receiverId = :user2) OR (m.senderId = :user2 AND m.receiverId = :user1) ORDER BY m.createdAt ASC")
    List<MessagePO> findConversationMessages(@Param("user1") Integer user1, @Param("user2") Integer user2);

    List<MessagePO> findByReceiverIdAndIsReadFalseOrderByCreatedAtAsc(Integer receiverId);

    long countByReceiverIdAndIsReadFalse(Integer receiverId);

    @Modifying
    @Transactional
    @Query("UPDATE MessagePO m SET m.isRead = true WHERE m.receiverId = :receiverId AND m.senderId = :senderId AND m.isRead = false")
    void markAsRead(@Param("receiverId") Integer receiverId, @Param("senderId") Integer senderId);
}
