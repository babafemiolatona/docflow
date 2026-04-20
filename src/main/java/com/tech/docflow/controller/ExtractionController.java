package com.tech.docflow.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.tech.docflow.dto.ExtractedFieldDTO;
import com.tech.docflow.exception.ResourceNotFoundException;
import com.tech.docflow.exception.AccessDeniedException;
import com.tech.docflow.models.ExtractedField;
import com.tech.docflow.models.User;
import com.tech.docflow.repository.DocumentRepository;
import com.tech.docflow.repository.ExtractedFieldRepository;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/documents/{documentId}/fields")
public class ExtractionController {
    
    private final ExtractedFieldRepository extractedFieldRepository;
    private final DocumentRepository documentRepository;
    
    @GetMapping
    @Operation(summary = "Get extracted fields for a document")
    public ResponseEntity<List<ExtractedFieldDTO>> getExtractedFields(
            @PathVariable Long documentId,
            @AuthenticationPrincipal User user) {
        
        var document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        
        if (!document.getOwner().getId().equals(user.getId())) {
            throw new AccessDeniedException("Access denied: You do not have permission to view this document");
        }
        
        List<ExtractedFieldDTO> fields = extractedFieldRepository
                .findByDocumentId(documentId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(fields);
    }
    
    private ExtractedFieldDTO mapToDTO(ExtractedField field) {
        return new ExtractedFieldDTO(
            field.getId(),
            field.getFieldName(),
            field.getFieldValue(),
            field.getConfidence(),
            field.getIsVerified()
        );
    }
}