package com.tech.docflow.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalActionRequest {
    private String action; // "approve" or "reject"
    private String comments;
    private String rejectionReason;
}