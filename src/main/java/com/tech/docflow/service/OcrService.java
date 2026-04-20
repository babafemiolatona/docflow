package com.tech.docflow.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


import com.tech.docflow.config.RabbitMqConfig;
import com.tech.docflow.models.Document;
import com.tech.docflow.models.DocumentStatus;
import com.tech.docflow.models.ExtractedField;
import com.tech.docflow.models.OcrJob;
import com.tech.docflow.models.OcrJobStatus;
import com.tech.docflow.models.OcrResult;
import com.tech.docflow.repository.DocumentRepository;
import com.tech.docflow.repository.ExtractedFieldRepository;
import com.tech.docflow.repository.OcrJobRepository;
import com.tech.docflow.repository.OcrResultRepository;
import com.tech.docflow.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OcrService {
    
    private final OcrJobRepository ocrJobRepository;
    private final OcrResultRepository ocrResultRepository;
    private final DocumentRepository documentRepository;
    private final ExtractedFieldRepository extractedFieldRepository;
    private final FileStorageService fileStorageService;
    private final TextExtractionService textExtractionService;
    private final FieldExtractionService fieldExtractionService;
    private final WorkflowService workflowService;
    private final RabbitTemplate rabbitTemplate;
    
    @Value("${ocr.max-retries:3}")
    private Integer maxRetries;
    
    @Transactional
    public void submitForOcr(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
        
        OcrJob ocrJob = new OcrJob();
        ocrJob.setDocument(document);
        ocrJob.setStatus(OcrJobStatus.PENDING);
        ocrJob.setRetryCount(0);
        ocrJobRepository.save(ocrJob);
        
        document.setStatus(DocumentStatus.PROCESSING);
        documentRepository.save(document);
        
        rabbitTemplate.convertAndSend(
            RabbitMqConfig.OCR_EXCHANGE,
            RabbitMqConfig.OCR_ROUTING_KEY,
            documentId.toString()
        );
        
        log.info("OCR job submitted for document {}", documentId);
    }
    
    @Transactional
    public void processOcrJob(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
        
        OcrJob ocrJob = null;
        int retries = 0;
        while (ocrJob == null && retries < 10) {
            ocrJob = ocrJobRepository.findByDocumentId(documentId).orElse(null);
            if (ocrJob == null) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for OCR job");
                }
                retries++;
            }
        }
        
        if (ocrJob == null) {
            throw new RuntimeException("OCR job not found for document: " + documentId);
        }
        
        if (document.getStoragePath() == null) {
            throw new RuntimeException("Document has no file attached");
        }
        
        ocrJob.setStatus(OcrJobStatus.PROCESSING);
        ocrJobRepository.save(ocrJob);
        
        try {
            InputStream fileStream = fileStorageService.downloadFile(document.getStoragePath());
            byte[] fileContent = fileStream.readAllBytes();
            
            String extractedText = textExtractionService.extractTextFromDocument(
                new ByteArrayInputStream(fileContent),
                document.getStoragePath()
            );
            
            OcrResult ocrResult = new OcrResult();
            ocrResult.setDocument(document);
            ocrResult.setRawText(extractedText);
            ocrResult.setConfidence(0.95);
            ocrResultRepository.save(ocrResult);
            
            Map<String, String> extractedFields = fieldExtractionService.extractFields(
                extractedText,
                document.getDocumentType()
            );
            
            extractedFields.forEach((fieldName, fieldValue) -> {
                ExtractedField field = new ExtractedField();
                field.setDocument(document);
                field.setFieldName(fieldName);
                field.setFieldValue(fieldValue);
                field.setConfidence(0.85); // Default confidence for regex extraction
                extractedFieldRepository.save(field);
            });
            
            ocrJob.setStatus(OcrJobStatus.COMPLETED);
            ocrJob.setRetryCount(ocrJob.getRetryCount());
            ocrJobRepository.save(ocrJob);
            
            document.setStatus(DocumentStatus.OCR_COMPLETE);
            documentRepository.save(document);
            
            log.info("OCR processing completed for document {}", documentId);
            
        } catch (Exception e) {
            log.error("OCR job for document {} failed with error: {}", documentId, e.getMessage(), e);
            ocrJob.setRetryCount(ocrJob.getRetryCount() + 1);
            ocrJob.setErrorMessage(e.getMessage());
            
            if (ocrJob.getRetryCount() < maxRetries) {
                ocrJob.setStatus(OcrJobStatus.RETRYING);
                log.warn("OCR job for document {} failed. Retry {} of {}", 
                    documentId, ocrJob.getRetryCount(), maxRetries);
            } else {
                ocrJob.setStatus(OcrJobStatus.FAILED);
                document.setStatus(DocumentStatus.UPLOADED);
                documentRepository.save(document);
                log.error("OCR job for document {} failed after {} retries", documentId, maxRetries);
            }
            ocrJobRepository.save(ocrJob);
            throw new RuntimeException("OCR processing failed: " + e.getMessage(), e);
        }
        
        initiateWorkflowAsync(documentId);
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void initiateWorkflowAsync(Long documentId) {
        try {
            workflowService.initiateWorkflow(documentId);
        } catch (Exception e) {
            log.warn("Failed to initiate workflow for document {}: {}", documentId, e.getMessage());
        }
    }
}