package com.tech.docflow.repository;

import com.tech.docflow.models.OcrJob;
import com.tech.docflow.models.OcrJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OcrJobRepository extends JpaRepository<OcrJob, Long> {
    Optional<OcrJob> findByDocumentId(Long documentId);
    List<OcrJob> findByStatus(OcrJobStatus status);
}
