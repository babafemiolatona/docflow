package com.tech.docflow.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.tech.docflow.dto.DocumentDTO;
import com.tech.docflow.exception.AccessDeniedException;
import com.tech.docflow.exception.ResourceNotFoundException;
import com.tech.docflow.models.AuditEvent;
import com.tech.docflow.models.Document;
import com.tech.docflow.models.DocumentStatus;
import com.tech.docflow.models.DocumentType;
import com.tech.docflow.models.User;
import com.tech.docflow.repository.AuditEventRepository;
import com.tech.docflow.repository.DocumentRepository;
import com.tech.docflow.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DocumentService{

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final AuditEventRepository auditEventRepository;
    private final OcrService ocrService;

    @Transactional
    public DocumentDTO uploadDocument(String ownerEmail, String title, DocumentType documentType, 
                                    String description, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        User owner = userRepository.findByEmail(ownerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User with email " + ownerEmail + " not found"));

        try {
            byte[] fileContent = file.getBytes();
            String fileChecksum = calculateChecksum(new ByteArrayInputStream(fileContent));

            String storagePath = fileStorageService.uploadFile(
                file.getOriginalFilename(),
                new ByteArrayInputStream(fileContent),
                file.getContentType(),
                file.getSize()
            );

            Document document = new Document();
            document.setTitle(title);
            document.setDocumentType(documentType);
            document.setOwner(owner);
            document.setDescription(description);
            document.setStatus(DocumentStatus.UPLOADED);
            document.setStoragePath(storagePath);
            document.setFileChecksum(fileChecksum);

            Document saved = documentRepository.save(document);
            ocrService.submitForOcr(saved.getId());

            AuditEvent event = new AuditEvent();
            event.setActor(owner);
            event.setAction("DOCUMENT_UPLOADED");
            event.setEntityType("DOCUMENT");
            event.setEntityId(saved.getId());
            event.setDescription(String.format("File uploaded: %s (%d bytes)", 
                file.getOriginalFilename(), file.getSize()));
            auditEventRepository.save(event);

            return mapToDTO(saved);
        } catch (IOException e) {
            throw new RuntimeException("Error processing uploaded file: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public byte[] downloadDocument(Long documentId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User with email " + userEmail + " not found"));

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        if (!document.getOwner().getId().equals(user.getId())) {
            throw new AccessDeniedException("Unauthorized access to document");
        }

        if (document.getStoragePath() == null) {
            throw new IllegalArgumentException("Document has no file attached");
        }

        try {
            return fileStorageService.downloadFile(document.getStoragePath()).readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Error downloading file: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public String getDocumentFileUrl(Long documentId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User with email " + userEmail + " not found"));

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        if (!document.getOwner().getId().equals(user.getId())) {
            throw new AccessDeniedException("Unauthorized access to document");
        }

        if (document.getStoragePath() == null) {
            throw new IllegalArgumentException("Document has no file attached");
        }

        return fileStorageService.getFileUrl(document.getStoragePath());
    }

    private String calculateChecksum(InputStream fileStream) throws IOException {
        MessageDigest digest = null;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }

        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = fileStream.read(buffer)) != -1) {
            digest.update(buffer, 0, bytesRead);
        }

        byte[] hashBytes = digest.digest();
        return HexFormat.of().formatHex(hashBytes);
    }

    @Transactional(readOnly = true)
    public Document getDocumentEntity(Long documentId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User with email " + userEmail + " not found"));

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        if (!document.getOwner().getId().equals(user.getId())) {
            throw new AccessDeniedException("Unauthorized access to document");
        }

        return document;
    }

    private DocumentDTO mapToDTO(Document document) {
        DocumentDTO dto = new DocumentDTO();
        dto.setId(document.getId());
        dto.setTitle(document.getTitle());
        dto.setDocumentType(document.getDocumentType());
        dto.setDescription(document.getDescription());
        dto.setStatus(document.getStatus());
        dto.setOwnerEmail(document.getOwner().getEmail());
        dto.setMetadataJson(document.getMetadataJson());
        dto.setCreatedAt(document.getCreatedAt());
        dto.setUpdatedAt(document.getUpdatedAt());
        
        if (document.getStoragePath() != null) {
            dto.setFileChecksum(document.getFileChecksum());
            dto.setFileUrl(fileStorageService.getFileUrl(document.getStoragePath()));
            
            String[] parts = document.getStoragePath().split("/");
            dto.setOriginalFileName(parts[parts.length - 1]);
        }
        
        return dto;
    }
}