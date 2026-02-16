package com.aykhedma.repository;

import com.aykhedma.model.service.RiskLevel;
import com.aykhedma.model.service.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceTypeRepository extends JpaRepository<ServiceType, Long> {

    List<ServiceType> findByCategoryId(Long categoryId);

    List<ServiceType> findByRiskLevel(RiskLevel riskLevel);

    @Query("SELECT st FROM ServiceType st WHERE st.category.id = :categoryId AND st.riskLevel = :riskLevel")
    List<ServiceType> findByCategoryAndRiskLevel(@Param("categoryId") Long categoryId,
                                                 @Param("riskLevel") RiskLevel riskLevel);

    @Query("SELECT new map(st.id as id, st.name as name, st.nameAr as nameAr, " +
            "st.category.id as categoryId, st.category.name as categoryName, " +
            "st.basePrice as basePrice, st.defaultPriceType as defaultPriceType) " +
            "FROM ServiceType st ORDER BY st.name")
    List<Object[]> findAllForDropdown();

    @Query("SELECT new map(st.id as id, st.name as name, st.nameAr as nameAr, " +
            "st.category.id as categoryId, st.category.name as categoryName, " +
            "st.basePrice as basePrice, st.defaultPriceType as defaultPriceType) " +
            "FROM ServiceType st WHERE st.category.id = :categoryId " +
            "ORDER BY st.name")
    List<Object[]> findByCategoryIdForDropdown(@Param("categoryId") Long categoryId);

    @Query("SELECT new map(st.id as id, st.name as name, st.nameAr as nameAr, st.riskLevel as riskLevel, " +
            "st.basePrice as basePrice, st.defaultPriceType as defaultPriceType) " +
            "FROM ServiceType st WHERE st.id = :id")
    Object findWithRiskById(@Param("id") Long id);

    @Query("SELECT COUNT(st) FROM ServiceType st")
    long countServices();
}