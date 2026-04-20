package com.tech.docflow.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tech.docflow.exception.AccessDeniedException;
import com.tech.docflow.exception.ResourceNotFoundException;
import com.tech.docflow.models.ApprovalStatus;
import com.tech.docflow.models.ApprovalTask;
import com.tech.docflow.models.Document;
import com.tech.docflow.models.DocumentStatus;
import com.tech.docflow.models.User;
import com.tech.docflow.models.UserRole;
import com.tech.docflow.models.WorkflowDefinition;
import com.tech.docflow.repository.ApprovalTaskRepository;
import com.tech.docflow.repository.DocumentRepository;
import com.tech.docflow.repository.UserRepository;
import com.tech.docflow.repository.WorkflowDefinitionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowService {
    
    private final WorkflowDefinitionRepository workflowRepository;
    private final ApprovalTaskRepository approvalTaskRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    // private final NotificationService notificationService;
    
    @Transactional
    public void initiateWorkflow(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
        
        WorkflowDefinition workflow = workflowRepository.findByDocumentTypeAndStatus(
                document.getDocumentType(), 
                com.tech.docflow.models.WorkflowStatus.ACTIVE
        ).orElseThrow(() -> new RuntimeException("No active workflow for document type: " + document.getDocumentType()));
        
        createApprovalTask(document, workflow, 1);
        
        document.setStatus(DocumentStatus.PENDING_APPROVAL);
        documentRepository.save(document);
        
        log.info("Workflow initiated for document {}", documentId);
    }
    
    @Transactional
    private void createApprovalTask(Document document, WorkflowDefinition workflow, Integer level) {
        User approver = userRepository.findByRoleAndIsActive(
            UserRole.APPROVER, true
        ).stream().findFirst()
         .orElseThrow(() -> new RuntimeException("No approvers available"));
        
        ApprovalTask task = new ApprovalTask();
        task.setDocument(document);
        task.setWorkflow(workflow);
        task.setAssignee(approver);
        task.setApprovalLevel(level);
        task.setStatus(ApprovalStatus.PENDING);
        task.setDueDate(Instant.now().plus(3, ChronoUnit.DAYS)); // 3-day SLA
        
        approvalTaskRepository.save(task);
        
        // notificationService.notifyTaskAssigned(task);
        
        log.info("Approval task created for document {} at level {}", document.getId(), level);
    }
    
    @Transactional
    public void approveTask(Long taskId, String approverEmail) {
        ApprovalTask task = approvalTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        
        if (!task.getAssignee().getEmail().equals(approverEmail)) {
            throw new AccessDeniedException("Not authorized to approve this task");
        }
        
        task.setStatus(ApprovalStatus.APPROVED);
        task.setCompletedAt(Instant.now());
        approvalTaskRepository.save(task);
        
        Document document = task.getDocument();
        
        if (isAllLevelsApproved(document)) {
            document.setStatus(DocumentStatus.APPROVED);
            documentRepository.save(document);
            // notificationService.notifyDocumentApproved(document);
            log.info("Document {} fully approved", document.getId());
        } else {
            createApprovalTask(document, task.getWorkflow(), task.getApprovalLevel() + 1);
        }
    }
    
    @Transactional
    public void rejectTask(Long taskId, String approverEmail, String rejectionReason) {
        ApprovalTask task = approvalTaskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));
        
        if (!task.getAssignee().getEmail().equals(approverEmail)) {
            throw new AccessDeniedException("Not authorized to reject this task");
        }
        
        task.setStatus(ApprovalStatus.REJECTED);
        task.setRejectionReason(rejectionReason);
        task.setCompletedAt(Instant.now());
        approvalTaskRepository.save(task);
        
        Document document = task.getDocument();
        document.setStatus(DocumentStatus.REVISION_REQUESTED);
        documentRepository.save(document);
        
        // notificationService.notifyDocumentRejected(document, rejectionReason);
        log.info("Document {} rejected at level {}", document.getId(), task.getApprovalLevel());
    }
    
    private boolean isAllLevelsApproved(Document document) {
        List<ApprovalTask> allTasks = approvalTaskRepository.findByDocumentId(document.getId());
        return allTasks.stream()
                .allMatch(task -> task.getStatus() == ApprovalStatus.APPROVED);
    }
}