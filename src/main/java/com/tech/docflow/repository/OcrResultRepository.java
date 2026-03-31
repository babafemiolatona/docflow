package com.tech.docflow.repository;

import com.tech.docflow.models.OcrResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OcrResultRepository extends JpaRepository<OcrResult, Long> {
    Optional<OcrResult> findByDocumentId(Long documentId);
}