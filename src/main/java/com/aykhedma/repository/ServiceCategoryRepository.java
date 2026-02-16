package com.aykhedma.repository;

import com.aykhedma.model.service.ServiceCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceCategoryRepository extends JpaRepository<ServiceCategory, Long> {

    Optional<ServiceCategory> findByName(String name);

    @Query("SELECT sc FROM ServiceCategory sc LEFT JOIN FETCH sc.serviceTypes ")
    List<ServiceCategory> findAllWithServiceTypes();

    @Query("SELECT new map(sc.id as id, sc.name as name, sc.nameAr as nameAr) " +
            "FROM ServiceCategory sc")
    List<Object[]> findAllForDropdown();

    @Query("SELECT COUNT(sc) FROM ServiceCategory sc")
    long countCategories();
}