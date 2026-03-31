package com.tech.docflow.controller;

import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tech.docflow.dto.DocumentDTO;
import com.tech.docflow.models.Document;
import com.tech.docflow.models.DocumentType;
import com.tech.docflow.models.User;
import com.tech.docflow.service.DocumentService;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping("/upload")
    @Operation(summary = "Upload a document", description = "Upload a document file with metadata")
    public ResponseEntity<DocumentDTO> uploadDocument(
            String title,
            DocumentType documentType,
            String description,
            MultipartFile file,
            @AuthenticationPrincipal User user
    ) {        
        String userEmail = user.getEmail();
        DocumentDTO document = documentService.uploadDocument(userEmail, title, documentType, description, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(document);
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download document file", description = "Download the file associated with a document")
    public ResponseEntity<byte[]> downloadDocument(@PathVariable Long id,
                                                @AuthenticationPrincipal User user) {
        String userEmail = user.getEmail();
        byte[] fileContent = documentService.downloadDocument(id, userEmail);
        
        Document document = documentService.getDocumentEntity(id, userEmail);
        String fileName = document.getTitle().replaceAll("[^a-zA-Z0-9._-]", "_") + getFileExtension(document);
        
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, 
                ContentDisposition.attachment().filename(fileName).build().toString())
            .header(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
            .body(fileContent);
    }

    @GetMapping("/{id}/file-url")
    @Operation(summary = "Get file download URL", description = "Get a presigned URL for downloading the document file")
    public ResponseEntity<Map<String, String>> getFileUrl(@PathVariable Long id,
                                                        @AuthenticationPrincipal User user) {
        String userEmail = user.getEmail();
        String fileUrl = documentService.getDocumentFileUrl(id, userEmail);
        return ResponseEntity.ok(Map.of("fileUrl", fileUrl));
    }

    private String getFileExtension(Document document) {
        String storagePath = document.getStoragePath();
        if (storagePath == null) return "";
        int lastDot = storagePath.lastIndexOf('.');
        return lastDot > 0 ? storagePath.substring(lastDot) : "";
    }
}