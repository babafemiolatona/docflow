package com.tech.docflow.dto;

import com.tech.docflow.models.ApprovalStatus;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalTaskDTO {
    private Long id;
    private Long documentId;
    private Long assigneeId;
    private String assigneeName;
    private String assigneeEmail;
    private ApprovalStatus status;
    private Integer approvalLevel;
    private Instant dueDate;
    private Instant completedAt;
    private String rejectionReason;
}