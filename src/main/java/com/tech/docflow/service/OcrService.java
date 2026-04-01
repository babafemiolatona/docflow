package com.tech.docflow.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tech.docflow.config.RabbitMqConfig;
import com.tech.docflow.models.Document;
import com.tech.docflow.models.DocumentStatus;
import com.tech.docflow.models.OcrJob;
import com.tech.docflow.models.OcrJobStatus;
import com.tech.docflow.models.OcrResult;
import com.tech.docflow.repository.DocumentRepository;
import com.tech.docflow.repository.OcrJobRepository;
import com.tech.docflow.repository.OcrResultRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OcrService {
    
    private final OcrJobRepository ocrJobRepository;
    private final OcrResultRepository ocrResultRepository;
    private final DocumentRepository documentRepository;
    private final FileStorageService fileStorageService;
    private final TextExtractionService textExtractionService;
    private final RabbitTemplate rabbitTemplate;
    
    @Value("${ocr.max-retries:3}")
    private Integer maxRetries;
    
    @Transactional
    public void submitForOcr(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));
        
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
        try {
            Document document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));
            
            OcrJob ocrJob = null;
            int retries = 0;
            while (ocrJob == null && retries < 10) {
                ocrJob = ocrJobRepository.findByDocumentId(documentId).orElse(null);
                if (ocrJob == null) {
                    Thread.sleep(100);
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
            
            ocrJob.setStatus(OcrJobStatus.COMPLETED);
            ocrJob.setRetryCount(ocrJob.getRetryCount());
            ocrJobRepository.save(ocrJob);
            
            document.setStatus(DocumentStatus.OCR_COMPLETE);
            documentRepository.save(document);
            
            log.info("OCR processing completed for document {}", documentId);
            
        } catch (Exception e) {
            handleOcrFailure(documentId, e);
        }
    }
    
    @Transactional
    private void handleOcrFailure(Long documentId, Exception e) {
        OcrJob ocrJob = ocrJobRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new RuntimeException("OCR job not found for document: " + documentId));
        
        ocrJob.setRetryCount(ocrJob.getRetryCount() + 1);
        ocrJob.setErrorMessage(e.getMessage());
        
        if (ocrJob.getRetryCount() < maxRetries) {
            ocrJob.setStatus(OcrJobStatus.RETRYING);
            log.warn("OCR job for document {} failed. Retry {} of {}", 
                documentId, ocrJob.getRetryCount(), maxRetries);
        } else {
            ocrJob.setStatus(OcrJobStatus.FAILED);
            Document document = ocrJobRepository.findByDocumentId(documentId)
                    .map(OcrJob::getDocument)
                    .orElse(null);
            if (document != null) {
                document.setStatus(DocumentStatus.UPLOADED);
                documentRepository.save(document);
            }
            log.error("OCR job for document {} failed after {} retries", documentId, maxRetries);
        }
        
        ocrJobRepository.save(ocrJob);
    }
}