package com.aykhedma.repository;

import com.aykhedma.model.document.Document;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByProviderId(Long providerId);

    List<Document> findByProviderIdAndType(Long providerId, String type);

    void deleteByProviderIdAndId(Long providerId, Long documentId);

    @Modifying
    @Query("DELETE FROM Document d WHERE d.provider.id = :providerId")
    void deleteByProviderId(@Param("providerId") Long providerId);
}