package com.tech.docflow.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.tech.docflow.models.Document;
import com.tech.docflow.models.DocumentStatus;
import com.tech.docflow.models.DocumentType;
import com.tech.docflow.models.User;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    
    List<Document> findByOwner(User owner);
    List<Document> findByOwnerAndStatus(User owner, DocumentStatus status);
    List<Document> findByDocumentType(DocumentType documentType);
    
    @Query("SELECT d FROM Document d WHERE d.owner = :owner AND d.createdAt >= :startDate AND d.createdAt <= :endDate")
    List<Document> findByOwnerAndDateRange(
        @Param("owner") User owner,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

}
