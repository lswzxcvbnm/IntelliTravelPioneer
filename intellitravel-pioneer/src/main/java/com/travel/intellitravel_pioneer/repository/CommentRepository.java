package com.travel.intellitravel_pioneer.repository;

import com.travel.intellitravel_pioneer.po.CommentPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<CommentPO, String> {
    List<CommentPO> findByPostIdOrderByCreatedAtDesc(String postId);

    void deleteAllByPostId(String postId);
}
