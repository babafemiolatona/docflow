package com.tech.docflow.repository;

import com.tech.docflow.models.ApprovalStatus;
import com.tech.docflow.models.ApprovalTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalTaskRepository extends JpaRepository<ApprovalTask, Long> {
    List<ApprovalTask> findByDocumentId(Long documentId);
    List<ApprovalTask> findByAssigneeIdAndStatus(Long assigneeId, ApprovalStatus status);
    Optional<ApprovalTask> findByDocumentIdAndApprovalLevel(Long documentId, Integer level);
}