package com.tech.docflow.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.tech.docflow.config.RabbitMqConfig;
import com.tech.docflow.service.OcrService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OcrJobListener {
    
    private final OcrService ocrService;
    
    @RabbitListener(queues = RabbitMqConfig.OCR_QUEUE)
    public void processOcrJob(String documentId) {
        try {
            Long docId = Long.parseLong(documentId);
            log.info("Processing OCR job for document {}", docId);
            ocrService.processOcrJob(docId);
        } catch (Exception e) {
            log.error("Error processing OCR job for document {}: {}", documentId, e.getMessage());
            throw e;
        }
    }
}