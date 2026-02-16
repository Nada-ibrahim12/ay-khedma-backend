package com.aykhedma.repository;

import com.aykhedma.model.document.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByProviderId(Long providerId);

    List<Document> findByProviderIdAndType(Long providerId, String type);

    void deleteByProviderIdAndId(Long providerId, Long documentId);
}