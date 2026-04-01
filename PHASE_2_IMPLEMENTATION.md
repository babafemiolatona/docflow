# Phase 2: OCR & Async Processing Implementation

**Goal:** Extract text from uploaded documents and build an async processing pipeline using RabbitMQ and Tika/Tesseract OCR.

**Estimated Duration:** 2-3 weeks of learning

---

## Overview of Phase 2 Architecture

```
Document Upload (Phase 1)
         ↓
    RabbitMQ Event
         ↓
OCR Job Service (async consumer)
         ↓
    Tika + Tesseract
         ↓
Store OcrResult + Document Status Update
         ↓
Structured Data Extraction Service
         ↓
Store DocumentMetadata (fieldName, fieldValue, confidence)
```

**Key Technologies:**
- **RabbitMQ**: Message broker for async event-driven architecture
- **Apache Tika**: Universal text extraction (PDFs, Word, Excel, images)
- **Tesseract**: OCR for scanned documents and images
- **Spring AMQP**: RabbitMQ integration with Spring Boot

---

## Step 1: Update Dependencies (pom.xml)

Add the following to your `<dependencies>` section:

```xml
<!-- RabbitMQ & Spring AMQP -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>

<!-- Apache Tika for document text extraction -->
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>2.9.1</version>
</dependency>
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-parsers-standard-package</artifactId>
    <version>2.9.1</version>
</dependency>

<!-- Tesseract OCR -->
<dependency>
    <groupId>net.sourceforge.tess4j</groupId>
    <artifactId>tess4j</artifactId>
    <version>5.10.0</version>
</dependency>

<!-- JSON parsing for structured extraction -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

**Rebuild project:**
```bash
mvn clean install
```

---

## Step 2: Update Docker Compose

Add RabbitMQ to `docker-compose.yml`:

```yaml
version: '3.8'

services:
  app:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/docflowdb
      - SPRING_DATASOURCE_USERNAME=postgres
      - SPRING_DATASOURCE_PASSWORD=postgres
      - SPRING_REDIS_HOST=redis
      - SPRING_RABBITMQ_HOST=rabbitmq
      - SPRING_RABBITMQ_PORT=5672
      - SPRING_RABBITMQ_USERNAME=guest
      - SPRING_RABBITMQ_PASSWORD=guest
      - FILE_STORAGE_TYPE=minio
      - MINIO_ENDPOINT=http://minio:9000
      - MINIO_ACCESS_KEY=${MINIO_ACCESS_KEY}
      - MINIO_SECRET_KEY=${MINIO_SECRET_KEY}
      - MINIO_BUCKET_NAME=docuflow-documents
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      minio:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
    networks:
      - docflow-network

  postgres:
    image: postgres:16-alpine
    environment:
      - POSTGRES_DB=docflowdb
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=postgres
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5
    volumes:
      - postgres_data:/var/lib/postgresql/data
    networks:
      - docflow-network

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
    volumes:
      - redis_data:/data
    networks:
      - docflow-network

  minio:
    image: minio/minio:latest
    environment:
      - MINIO_ROOT_USER=${MINIO_ACCESS_KEY}
      - MINIO_ROOT_PASSWORD=${MINIO_SECRET_KEY}
    ports:
      - "9000:9000"
      - "9001:9001"
    command: minio server /data --console-address ":9001"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 10s
      timeout: 5s
      retries: 5
    volumes:
      - minio_data:/data
    networks:
      - docflow-network

  rabbitmq:
    image: rabbitmq:3.12-management-alpine
    environment:
      - RABBITMQ_DEFAULT_USER=guest
      - RABBITMQ_DEFAULT_PASS=guest
    ports:
      - "5672:5672"
      - "15672:15672"
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq
    networks:
      - docflow-network

volumes:
  postgres_data:
  redis_data:
  minio_data:
  rabbitmq_data:

networks:
  docflow-network:
    driver: bridge
```

---

## Step 3: Create Phase 2 Entities

### 3.1 OcrJob Entity

Create `src/main/java/com/tech/docflow/models/OcrJob.java`:

```java
package com.tech.docflow.models;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import lombok.*;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ocr_jobs")
public class OcrJob {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OcrJobStatus status = OcrJobStatus.PENDING;
    
    @CreationTimestamp
    @Column(updatable = false)
    private Instant startedAt;
    
    @UpdateTimestamp
    private Instant completedAt;
    
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;
    
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
}
```

### 3.2 OcrJobStatus Enum

Create `src/main/java/com/tech/docflow/models/OcrJobStatus.java`:

```java
package com.tech.docflow.models;

public enum OcrJobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    RETRYING
}
```

### 3.3 OcrResult Entity

Create `src/main/java/com/tech/docflow/models/OcrResult.java`:

```java
package com.tech.docflow.models;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import lombok.*;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ocr_results")
public class OcrResult {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String rawText;
    
    @Column(columnDefinition = "TEXT")
    private String structuredFieldsJson;
    
    @Column(nullable = false)
    private Double confidence = 0.0;
    
    @CreationTimestamp
    private Instant processedAt;
}
```

### 3.4 DocumentMetadata Entity

Create `src/main/java/com/tech/docflow/models/DocumentMetadata.java`:

```java
package com.tech.docflow.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "document_metadata")
public class DocumentMetadata {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;
    
    @Column(nullable = false)
    private String fieldName;
    
    @Column(columnDefinition = "TEXT")
    private String fieldValue;
    
    @Column(nullable = false)
    private Double confidence = 0.0;
}
```

---

## Step 4: Create Repositories

### 4.1 OcrJobRepository

Create `src/main/java/com/tech/docflow/repository/OcrJobRepository.java`:

```java
package com.tech.docflow.repository;

import com.tech.docflow.models.OcrJob;
import com.tech.docflow.models.OcrJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OcrJobRepository extends JpaRepository<OcrJob, Long> {
    Optional<OcrJob> findByDocumentId(Long documentId);
    List<OcrJob> findByStatus(OcrJobStatus status);
}
```

### 4.2 OcrResultRepository

Create `src/main/java/com/tech/docflow/repository/OcrResultRepository.java`:

```java
package com.tech.docflow.repository;

import com.tech.docflow.models.OcrResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OcrResultRepository extends JpaRepository<OcrResult, Long> {
    Optional<OcrResult> findByDocumentId(Long documentId);
}
```

### 4.3 DocumentMetadataRepository

Create `src/main/java/com/tech/docflow/repository/DocumentMetadataRepository.java`:

```java
package com.tech.docflow.repository;

import com.tech.docflow.models.DocumentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, Long> {
    List<DocumentMetadata> findByDocumentId(Long documentId);
}
```

---

## Step 5: Configure RabbitMQ & Message Queue

### 5.1 RabbitMQ Configuration

Create `src/main/java/com/tech/docflow/config/RabbitMqConfig.java`:

```java
package com.tech.docflow.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {
    
    // Queue names
    public static final String OCR_QUEUE = "ocr.submission.queue";
    public static final String OCR_DLQ = "ocr.submission.dlq";
    public static final String OCR_EXCHANGE = "ocr.exchange";
    public static final String OCR_ROUTING_KEY = "ocr.submit";
    
    // Main OCR Queue
    @Bean
    public Queue ocrQueue() {
        return QueueBuilder.durable(OCR_QUEUE)
                .withArgument("x-dead-letter-exchange", "ocr.dlx")
                .withArgument("x-dead-letter-routing-key", "ocr.dlq")
                .build();
    }
    
    // Dead Letter Queue for failed OCR jobs
    @Bean
    public Queue ocrDeadLetterQueue() {
        return QueueBuilder.durable(OCR_DLQ).build();
    }
    
    // Exchange
    @Bean
    public DirectExchange ocrExchange() {
        return new DirectExchange(OCR_EXCHANGE, true, false);
    }
    
    // Dead Letter Exchange
    @Bean
    public DirectExchange ocrDeadLetterExchange() {
        return new DirectExchange("ocr.dlx", true, false);
    }
    
    // Bindings
    @Bean
    public Binding ocrBinding(Queue ocrQueue, DirectExchange ocrExchange) {
        return BindingBuilder.bind(ocrQueue)
                .to(ocrExchange)
                .with(OCR_ROUTING_KEY);
    }
    
    @Bean
    public Binding ocrDlqBinding(Queue ocrDeadLetterQueue, DirectExchange ocrDeadLetterExchange) {
        return BindingBuilder.bind(ocrDeadLetterQueue)
                .to(ocrDeadLetterExchange)
                .with("ocr.dlq");
    }
}
```

### 5.2 Application Configuration Update

Update `src/main/resources/application.yaml`:

```yaml
spring:
  application:
    name: docflow-document-management
  
  datasource:
    url: jdbc:postgresql://postgres:5432/docflowdb
    username: ${DB_USER:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver
  
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  
  redis:
    host: ${REDIS_HOST:redis}
    port: ${REDIS_PORT:6379}
  
  rabbitmq:
    host: ${RABBIT_HOST:rabbitmq}
    port: ${RABBIT_PORT:5672}
    username: ${RABBIT_USER:guest}
    password: ${RABBIT_PASSWORD:guest}
  
  servlet:
    multipart:
      max-file-size: 100MB
      max-request-size: 100MB

server:
  port: ${SERVER_PORT:8080}
  servlet:
    context-path: /api

file:
  storage:
    type: ${FILE_STORAGE_TYPE:minio}
    minio:
      endpoint: ${MINIO_ENDPOINT:http://minio:9000}
      access-key: ${MINIO_ACCESS_KEY:minio-admin}
      secret-key: ${MINIO_SECRET_KEY:nimda-minio}
      bucket-name: ${MINIO_BUCKET_NAME:docuflow-documents}
      region: ${MINIO_REGION:us-east-1}

jwt:
  secret: ${JWT_SECRET}
  access-token-expiration: ${JWT_ACCESS_TOKEN_EXPIRATION:900000}
  refresh-token-expiration: ${JWT_REFRESH_TOKEN_EXPIRATION:604800000}

ocr:
  max-retries: 3
  retry-delay-seconds: 10
  tika:
    enabled: true
  tesseract:
    enabled: true
    data-path: /usr/share/tesseract-ocr/4.00/tessdata
```

---

## Step 6: Create OCR Services

### 6.1 TextExtractionService (Tika)

Create `src/main/java/com/tech/docflow/service/TextExtractionService.java`:

```java
package com.tech.docflow.service;

import java.io.InputStream;
import java.io.IOException;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TextExtractionService {
    
    private final Tika tika = new Tika();
    
    /**
     * Extract text from any document format using Apache Tika
     * Supports: PDF, Word (DOCX), Excel (XLSX), PowerPoint, RTF, TXT, etc.
     */
    public String extractTextFromDocument(InputStream fileStream, String fileName) 
            throws IOException, TikaException, SAXException {
        try {
            return tika.parseToString(fileStream);
        } catch (TikaException e) {
            throw new RuntimeException("Tika error extracting text from " + fileName + ": " + e.getMessage(), e);
        } catch (IOException e) {
            throw new RuntimeException("IO error extracting text from " + fileName + ": " + e.getMessage(), e);
        } catch (SAXException e) {
            throw new RuntimeException("SAX error extracting text from " + fileName + ": " + e.getMessage(), e);
        }
    }
}
```

### 6.2 OcrService

Create `src/main/java/com/tech/docflow/service/OcrService.java`:

```java
package com.tech.docflow.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.xml.sax.SAXException;

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
    
    /**
     * Submit a document for OCR processing
     * Publishes DocumentUploaded event to RabbitMQ
     */
    @Transactional
    public void submitForOcr(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));
        
        // Create OCR Job
        OcrJob ocrJob = new OcrJob();
        ocrJob.setDocument(document);
        ocrJob.setStatus(OcrJobStatus.PENDING);
        ocrJob.setRetryCount(0);
        ocrJobRepository.save(ocrJob);
        
        // Update document status
        document.setStatus(DocumentStatus.PROCESSING);
        documentRepository.save(document);
        
        // Publish event to RabbitMQ
        rabbitTemplate.convertAndSend(
            RabbitMqConfig.OCR_EXCHANGE,
            RabbitMqConfig.OCR_ROUTING_KEY,
            documentId.toString()
        );
        
        log.info("OCR job submitted for document {}", documentId);
    }
    
    /**
     * Process OCR job (called by consumer)
     */
    @Transactional
    public void processOcrJob(Long documentId) {
        try {
            Document document = documentRepository.findById(documentId)
                    .orElseThrow(() -> new RuntimeException("Document not found: " + documentId));
            
            OcrJob ocrJob = ocrJobRepository.findByDocumentId(documentId)
                    .orElseThrow(() -> new RuntimeException("OCR job not found for document: " + documentId));
            
            if (document.getStoragePath() == null) {
                throw new RuntimeException("Document has no file attached");
            }
            
            // Mark as processing
            ocrJob.setStatus(OcrJobStatus.PROCESSING);
            ocrJobRepository.save(ocrJob);
            
            // Download file from MinIO
            InputStream fileStream = fileStorageService.downloadFile(document.getStoragePath());
            byte[] fileContent = fileStream.readAllBytes();
            
            // Extract text using Tika
            String extractedText = textExtractionService.extractTextFromDocument(
                new ByteArrayInputStream(fileContent),
                document.getStoragePath()
            );
            
            // Save OCR result
            OcrResult ocrResult = new OcrResult();
            ocrResult.setDocument(document);
            ocrResult.setRawText(extractedText);
            ocrResult.setConfidence(0.95); // Default confidence for Tika extraction
            ocrResultRepository.save(ocrResult);
            
            // Update OCR job status
            ocrJob.setStatus(OcrJobStatus.COMPLETED);
            ocrJob.setRetryCount(ocrJob.getRetryCount());
            ocrJobRepository.save(ocrJob);
            
            // Update document status
            document.setStatus(DocumentStatus.PROCESSING);
            documentRepository.save(document);
            
            log.info("OCR processing completed for document {}", documentId);
            
        } catch (Exception e) {
            handleOcrFailure(documentId, e);
        }
    }
    
    /**
     * Handle OCR job failures with retry logic
     */
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
```

### 6.3 OcrJobListener (RabbitMQ Consumer)

Create `src/main/java/com/tech/docflow/listener/OcrJobListener.java`:

```java
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
            throw e; // Let RabbitMQ handle retry/DLQ
        }
    }
}
```

---

## Step 7: Update DocumentService to Trigger OCR

Modify `DocumentService.uploadDocument()` to submit OCR job after upload:

```java
@Transactional
public DocumentDTO uploadDocument(String ownerEmail, String title, DocumentType documentType, 
                                String description, MultipartFile file) {
    // ... existing upload code ...
    
    Document saved = documentRepository.save(document);
    
    // NEW: Submit for OCR processing
    ocrService.submitForOcr(saved.getId());
    
    // ... rest of the code ...
}
```

Add `OcrService` to DocumentService constructor:

```java
private final OcrService ocrService;
```

---

## Step 8: Create DTOs for OCR Results

### 8.1 OcrResultDTO

Create `src/main/java/com/tech/docflow/dto/OcrResultDTO.java`:

```java
package com.tech.docflow.dto;

import java.time.Instant;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OcrResultDTO {
    private Long id;
    private Long documentId;
    private String rawText;
    private String structuredFieldsJson;
    private Double confidence;
    private Instant processedAt;
}
```

### 8.2 OcrJobDTO

Create `src/main/java/com/tech/docflow/dto/OcrJobDTO.java`:

```java
package com.tech.docflow.dto;

import com.tech.docflow.models.OcrJobStatus;
import java.time.Instant;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OcrJobDTO {
    private Long id;
    private Long documentId;
    private OcrJobStatus status;
    private Instant startedAt;
    private Instant completedAt;
    private Integer retryCount;
    private String errorMessage;
}
```

---

## Step 9: Create API Endpoints

### 9.1 OcrController

Create `src/main/java/com/tech/docflow/controller/OcrController.java`:

```java
package com.tech.docflow.controller;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.tech.docflow.dto.OcrJobDTO;
import com.tech.docflow.dto.OcrResultDTO;
import com.tech.docflow.models.User;
import com.tech.docflow.service.OcrJobQueryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/ocr")
@SecurityRequirement(name = "Bearer Authentication")
public class OcrController {
    
    private final OcrJobQueryService ocrJobQueryService;
    
    @GetMapping("/documents/{documentId}/results")
    @Operation(summary = "Get OCR results for a document")
    public ResponseEntity<OcrResultDTO> getOcrResults(
            @PathVariable Long documentId,
            @AuthenticationPrincipal User user) {
        OcrResultDTO result = ocrJobQueryService.getOcrResultByDocumentId(documentId, user.getEmail());
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/documents/{documentId}/status")
    @Operation(summary = "Get OCR job status for a document")
    public ResponseEntity<OcrJobDTO> getOcrStatus(
            @PathVariable Long documentId,
            @AuthenticationPrincipal User user) {
        OcrJobDTO status = ocrJobQueryService.getOcrJobStatus(documentId, user.getEmail());
        return ResponseEntity.ok(status);
    }
    
    @GetMapping("/jobs")
    @Operation(summary = "List all OCR jobs (Admin only)")
    public ResponseEntity<List<OcrJobDTO>> listOcrJobs() {
        List<OcrJobDTO> jobs = ocrJobQueryService.getAllOcrJobs();
        return ResponseEntity.ok(jobs);
    }
    
    @PostMapping("/documents/{documentId}/retry")
    @Operation(summary = "Manually retry OCR for a failed document")
    public ResponseEntity<Void> retryOcr(
            @PathVariable Long documentId,
            @AuthenticationPrincipal User user) {
        ocrJobQueryService.retryOcr(documentId, user.getEmail());
        return ResponseEntity.ok().build();
    }
}
```

### 9.2 OcrJobQueryService

Create `src/main/java/com/tech/docflow/service/OcrJobQueryService.java`:

```java
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
import com.tech.docflow.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OcrJobQueryService {
    
    private final OcrJobRepository ocrJobRepository;
    private final OcrResultRepository ocrResultRepository;
    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final OcrService ocrService;
    
    @Transactional(readOnly = true)
    public OcrResultDTO getOcrResultByDocumentId(Long documentId, String userEmail) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        
        if (!document.getOwner().getEmail().equals(userEmail)) {
            throw new SecurityException("Unauthorized access to document");
        }
        
        OcrResult result = ocrResultRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new RuntimeException("OCR results not found for document"));
        
        return mapToDto(result);
    }
    
    @Transactional(readOnly = true)
    public OcrJobDTO getOcrJobStatus(Long documentId, String userEmail) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Document not found"));
        
        if (!document.getOwner().getEmail().equals(userEmail)) {
            throw new SecurityException("Unauthorized access to document");
        }
        
        OcrJob job = ocrJobRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new RuntimeException("OCR job not found"));
        
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
                .orElseThrow(() -> new RuntimeException("Document not found"));
        
        if (!document.getOwner().getEmail().equals(userEmail)) {
            throw new SecurityException("Unauthorized access to document");
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
```

---

## Step 10: Database Migrations

Add to `src/main/resources/db/migration/` (if using Flyway):

**V2__Create_OCR_Tables.sql:**

```sql
CREATE TABLE ocr_jobs (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    retry_count INTEGER DEFAULT 0,
    error_message TEXT,
    UNIQUE(document_id)
);

CREATE TABLE ocr_results (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    raw_text TEXT NOT NULL,
    structured_fields_json TEXT,
    confidence DECIMAL(3,2) NOT NULL DEFAULT 0.95,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(document_id)
);

CREATE TABLE document_metadata (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    field_name VARCHAR(255) NOT NULL,
    field_value TEXT,
    confidence DECIMAL(3,2) NOT NULL DEFAULT 0.0
);

CREATE INDEX idx_ocr_jobs_status ON ocr_jobs(status);
CREATE INDEX idx_ocr_jobs_document_id ON ocr_jobs(document_id);
CREATE INDEX idx_ocr_results_document_id ON ocr_results(document_id);
CREATE INDEX idx_document_metadata_document_id ON document_metadata(document_id);
```

---

## Step 11: Update DocumentStatus Enum

Add to `DocumentStatus.java`:

```java
public enum DocumentStatus {
    UPLOADED,
    PROCESSING,
    OCR_COMPLETE,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED
}
```

---

## Step 12: Testing Phase 2

### 12.1 Integration Test

Create `src/test/java/com/tech/docflow/service/OcrServiceIntegrationTest.java`:

```java
package com.tech.docflow.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.tech.docflow.models.OcrJobStatus;
import com.tech.docflow.repository.OcrJobRepository;

@SpringBootTest
@ActiveProfiles("test")
class OcrServiceIntegrationTest {
    
    @Autowired
    private OcrService ocrService;
    
    @Autowired
    private OcrJobRepository ocrJobRepository;
    
    @Test
    void testTextExtraction() throws Exception {
        // Create a simple text document
        String testContent = "Hello World! This is a test document.";
        InputStream inputStream = new ByteArrayInputStream(testContent.getBytes());
        
        // Extract text
        // Add assertions...
    }
}
```

### 12.2 Manual Testing

1. **Start Docker containers:**
   ```bash
   docker-compose up --build
   ```

2. **Register and login to get JWT token**

3. **Upload a document:**
   ```bash
   curl -X POST http://localhost:8080/api/v1/documents/upload \
     -H "Authorization: Bearer YOUR_JWT_TOKEN" \
     -F "title=Test Invoice" \
     -F "documentType=INVOICE" \
     -F "description=Invoice from Acme" \
     -F "file=@invoice.pdf"
   ```

4. **Check OCR status:**
   ```bash
   curl -X GET http://localhost:8080/api/v1/ocr/documents/{documentId}/status \
     -H "Authorization: Bearer YOUR_JWT_TOKEN"
   ```

5. **Get OCR results:**
   ```bash
   curl -X GET http://localhost:8080/api/v1/ocr/documents/{documentId}/results \
     -H "Authorization: Bearer YOUR_JWT_TOKEN"
   ```

---

## Phase 2 Success Criteria

✅ Document upload triggers async OCR processing
✅ OCR results (raw text) stored in database
✅ OCR job status can be queried (PENDING → PROCESSING → COMPLETED)
✅ Failed OCR jobs retry up to 3 times
✅ Manual retry endpoint works
✅ RabbitMQ consumer processing jobs asynchronously
✅ All Phase 1 functionality still works
✅ API endpoints documented in Swagger

---

## Next Steps (Phase 3)

Once Phase 2 is complete:
1. Add structured data extraction (invoice fields, contract terms, etc.)
2. Implement workflow engine for approval processes
3. Create approval task assignment and status tracking
4. Add notification system for task assignments
