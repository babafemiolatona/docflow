package com.tech.docflow.repository;

import com.tech.docflow.models.DocumentType;
import com.tech.docflow.models.WorkflowDefinition;
import com.tech.docflow.models.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, Long> {
    Optional<WorkflowDefinition> findByDocumentTypeAndStatus(DocumentType documentType, WorkflowStatus status);
    List<WorkflowDefinition> findByStatus(WorkflowStatus status);
}