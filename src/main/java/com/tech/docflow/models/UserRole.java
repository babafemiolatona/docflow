package com.tech.docflow.models;

import lombok.*;

@Getter
@AllArgsConstructor
public enum UserRole {
    
    ADMIN("Administrator - Full access"),
    FINANCE("Finance Manager - Process invoices, approve payments"),
    LEGAL("Legal Officer - Review contracts, manage compliance"),
    APPROVER("General Approver - Approve documents based on workflows"),
    VIEWER("Read-only access - View documents and reports");

    private final String description;

}
