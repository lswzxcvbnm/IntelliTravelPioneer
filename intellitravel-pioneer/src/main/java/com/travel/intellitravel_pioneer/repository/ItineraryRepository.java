package com.travel.intellitravel_pioneer.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.travel.intellitravel_pioneer.po.ItineraryPO;

import java.util.List;

public interface ItineraryRepository extends JpaRepository<ItineraryPO, String> {
    List<ItineraryPO> findByUserId(String userId);
}