package com.tech.docflow.dto;

import java.time.Instant;

import com.tech.docflow.models.DocumentStatus;
import com.tech.docflow.models.DocumentType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDTO {
    
    private Long id;

    @NotBlank(message = "Title is required")
    private String title;

    @NotNull(message = "Document type is required")
    private DocumentType documentType;

    private String description;

    private DocumentStatus status;

    private String ownerEmail;

    private String metadataJson;

    private Instant createdAt;

    private Instant updatedAt;
}
