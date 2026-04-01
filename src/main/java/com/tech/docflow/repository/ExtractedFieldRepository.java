package com.tech.docflow.repository;

import com.tech.docflow.models.ExtractedField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExtractedFieldRepository extends JpaRepository<ExtractedField, Long> {
    List<ExtractedField> findByDocumentId(Long documentId);
    Optional<ExtractedField> findByDocumentIdAndFieldName(Long documentId, String fieldName);
}