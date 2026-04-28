package com.travel.intellitravel_pioneer.repository;

import com.travel.intellitravel_pioneer.po.PostPO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PostRepository extends JpaRepository<PostPO, String> {
    
    // For sorting by hot (calculated field? or just use like_count/comment_count/etc)
    // Assuming simple sort for now based on fields
    
    Page<PostPO> findByTagsContaining(String tag, Pageable pageable);

    @Query("SELECT p FROM PostPO p WHERE (:tag IS NULL OR p.tags LIKE %:tag%)")
    Page<PostPO> findAllWithFilter(String tag, Pageable pageable);

    Page<PostPO> findByUserId(Integer userId, Pageable pageable);
}
