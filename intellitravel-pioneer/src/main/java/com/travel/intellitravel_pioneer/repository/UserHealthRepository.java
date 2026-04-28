package com.travel.intellitravel_pioneer.repository;

import com.travel.intellitravel_pioneer.po.UserHealthPO;
import org.springframework.data.repository.CrudRepository;

public interface UserHealthRepository extends CrudRepository<UserHealthPO, Integer> {
    UserHealthPO findByUserId(Integer userId);
    UserHealthPO findByUsername(String username);
}