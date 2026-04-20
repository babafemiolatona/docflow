package com.tech.docflow.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tech.docflow.dto.OcrJobDTO;
import com.tech.docflow.dto.OcrResultDTO;
import com.tech.docflow.models.Document;
import com.tech.docflow.models.OcrJob;
import com.tech.docflow.models.OcrResult;
import com.tech.docflow.repository.DocumentRepository;
import com.tech.docflow.repository.OcrJobRepository;
import com.tech.docflow.repository.OcrResultRepository;

import com.tech.docflow.exception.AccessDeniedException;
import com.tech.docflow.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OcrJobQueryService {
    
    private final OcrJobRepository ocrJobRepository;
    private final OcrResultRepository ocrResultRepository;
    private final DocumentRepository documentRepository;
    private final OcrService ocrService;
    
    @Transactional(readOnly = true)
    public OcrResultDTO getOcrResultByDocumentId(Long documentId, String userEmail) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        
        if (!document.getOwner().getEmail().equals(userEmail)) {
            throw new AccessDeniedException("Unauthorized access to document");
        }
        
        OcrResult result = ocrResultRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("OCR results not found for document"));
        
        return mapToDto(result);
    }
    
    @Transactional(readOnly = true)
    public OcrJobDTO getOcrJobStatus(Long documentId, String userEmail) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        
        if (!document.getOwner().getEmail().equals(userEmail)) {
            throw new AccessDeniedException("Unauthorized access to document");
        }
        
        OcrJob job = ocrJobRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("OCR job not found"));
        
        return mapToDto(job);
    }
    
    @Transactional(readOnly = true)
    public List<OcrJobDTO> getAllOcrJobs() {
        return ocrJobRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public void retryOcr(Long documentId, String userEmail) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        
        if (!document.getOwner().getEmail().equals(userEmail)) {
            throw new AccessDeniedException("Unauthorized access to document");
        }
        
        ocrService.submitForOcr(documentId);
    }
    
    private OcrResultDTO mapToDto(OcrResult result) {
        return new OcrResultDTO(
            result.getId(),
            result.getDocument().getId(),
            result.getRawText(),
            result.getStructuredFieldsJson(),
            result.getConfidence(),
            result.getProcessedAt()
        );
    }
    
    private OcrJobDTO mapToDto(OcrJob job) {
        return new OcrJobDTO(
            job.getId(),
            job.getDocument().getId(),
            job.getStatus(),
            job.getStartedAt(),
            job.getCompletedAt(),
            job.getRetryCount(),
            job.getErrorMessage()
        );
    }
}