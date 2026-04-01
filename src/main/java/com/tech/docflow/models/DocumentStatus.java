package com.tech.docflow.models;

import lombok.*;

@Getter
public enum DocumentStatus {
    
    UPLOADED("Document uploaded, pending processing"),
    PROCESSING("OCR and data extraction in progress"),
    OCR_COMPLETE("Text extraction complete, awaiting workflow"),
    PENDING_APPROVAL("Awaiting approval from assigned approvers"),
    APPROVED("Document approved"),
    REJECTED("Document rejected, reason recorded"),
    REVISION_REQUESTED("Awaiting document revision"),
    ARCHIVED("Document archived, no longer active");

    private final String description;
    
    DocumentStatus(String description) {
        this.description = description;
    }
}
