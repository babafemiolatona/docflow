package com.tech.docflow.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.tech.docflow.dto.ApprovalActionRequest;
import com.tech.docflow.dto.ApprovalTaskDTO;
import com.tech.docflow.models.ApprovalStatus;
import com.tech.docflow.models.User;
import com.tech.docflow.repository.ApprovalTaskRepository;
import com.tech.docflow.service.WorkflowService;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/approvals")
public class ApprovalController {
    
    private final WorkflowService workflowService;
    private final ApprovalTaskRepository approvalTaskRepository;
    
    @GetMapping("/my-tasks")
    @Operation(summary = "Get pending approval tasks for current user")
    public ResponseEntity<List<ApprovalTaskDTO>> getMyApprovalTasks(
            @AuthenticationPrincipal User user) {
        List<ApprovalTaskDTO> tasks = approvalTaskRepository
                .findByAssigneeIdAndStatus(user.getId(), ApprovalStatus.PENDING)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(tasks);
    }
    
    @GetMapping("/{taskId}")
    @Operation(summary = "Get details of a specific approval task")
    public ResponseEntity<ApprovalTaskDTO> getApprovalTask(
            @PathVariable Long taskId,
            @AuthenticationPrincipal User user) {
        var task = approvalTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));
        return ResponseEntity.ok(mapToDTO(task));
    }
    
    @PostMapping("/{taskId}/approve")
    @Operation(summary = "Approve a document")
    public ResponseEntity<Void> approveTask(
            @PathVariable Long taskId,
            @RequestBody ApprovalActionRequest request,
            @AuthenticationPrincipal User user) {
        workflowService.approveTask(taskId, user.getEmail());
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/{taskId}/reject")
    @Operation(summary = "Reject a document with reason")
    public ResponseEntity<Void> rejectTask(
            @PathVariable Long taskId,
            @RequestBody ApprovalActionRequest request,
            @AuthenticationPrincipal User user) {
        workflowService.rejectTask(taskId, user.getEmail(), request.getRejectionReason());
        return ResponseEntity.ok().build();
    }
    
    private ApprovalTaskDTO mapToDTO(com.tech.docflow.models.ApprovalTask task) {
        return new ApprovalTaskDTO(
            task.getId(),
            task.getDocument().getId(),
            task.getAssignee().getId(),
            task.getAssignee().getFirstName() + " " + task.getAssignee().getLastName(),
            task.getAssignee().getEmail(),
            task.getStatus(),
            task.getApprovalLevel(),
            task.getDueDate(),
            task.getCompletedAt(),
            task.getRejectionReason()
        );
    }
}