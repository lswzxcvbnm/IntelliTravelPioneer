package com.travel.intellitravel_pioneer.repository;

import com.travel.intellitravel_pioneer.po.UserPO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserPO, Integer> {
    UserPO findByUsername(String username);
    Integer findIdByUsername(String username);
}
