package com.travel.intellitravel_pioneer.repository;

import com.travel.intellitravel_pioneer.po.CapsulePO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CapsuleRepository extends JpaRepository<CapsulePO, Long> {
    List<CapsulePO> findByUserId(Long userId);
    List<CapsulePO> findByLocationName(String locationName); // 简单按照地名查询，后续可扩展经纬度范围查询
    List<CapsulePO> findByCity(String city);
    List<CapsulePO> findByAdcode(String adcode);
    List<CapsulePO> findByLocationNameContainingOrAddressContaining(String locationName, String address);

    // 用 city + 关键字增强搜索准确度
    @org.springframework.data.jpa.repository.Query("SELECT c FROM CapsulePO c WHERE c.city = :city AND (c.locationName LIKE %:keyword% OR c.address LIKE %:keyword%)")
    List<CapsulePO> findByCityAndKeyword(@org.springframework.data.repository.query.Param("city") String city,
                                         @org.springframework.data.repository.query.Param("keyword") String keyword);

    // 基于经纬度范围查找，用于距离判断优化
    List<CapsulePO> findByLatitudeBetweenAndLongitudeBetween(Double minLatitude, Double maxLatitude, Double minLongitude, Double maxLongitude);
}
