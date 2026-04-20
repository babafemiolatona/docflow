package com.tech.docflow.models;

import lombok.*;

@Getter
@AllArgsConstructor
public enum DocumentType {
    
    INVOICE("Invoice"),
    CONTRACT("Contract"),
    RESUME("Resume"),
    FORM("Form"),
    COMPLIANCE_DOCUMENT("Compliance Document"),
    OTHER("Other");

    private final String displayName;

}
