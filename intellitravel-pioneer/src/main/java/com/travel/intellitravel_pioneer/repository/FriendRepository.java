package com.travel.intellitravel_pioneer.repository;

import com.travel.intellitravel_pioneer.po.FriendPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendRepository extends JpaRepository<FriendPO, Integer> {

    Optional<FriendPO> findByUser1IdAndUser2Id(Integer user1Id, Integer user2Id);

    @Query("SELECT f FROM FriendPO f WHERE (f.user1Id = :userId1 AND f.user2Id = :userId2) OR (f.user1Id = :userId2 AND f.user2Id = :userId1)")
    Optional<FriendPO> findFriendship(@Param("userId1") Integer userId1, @Param("userId2") Integer userId2);

    List<FriendPO> findByUser1IdOrderByCreatedAtDesc(Integer user1Id);

    List<FriendPO> findByUser2IdOrderByCreatedAtDesc(Integer user2Id);

    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM FriendPO f WHERE (f.user1Id = :userId1 AND f.user2Id = :userId2) OR (f.user1Id = :userId2 AND f.user2Id = :userId1)")
    boolean existsFriendship(@Param("userId1") Integer userId1, @Param("userId2") Integer userId2);

    void deleteByUser1IdAndUser2Id(Integer user1Id, Integer user2Id);
}
