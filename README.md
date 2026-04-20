# DocFlow - Intelligent Document Processing System

An enterprise-grade document processing platform that combines OCR technology, AI-powered field extraction, and multi-level approval workflows to automate document handling and data extraction at scale.

---

## Key Features

- **Document Processing Pipeline** - Upload → OCR (Tesseract) → Field Extraction (Phi LLM) → Approval Workflows
- **Intelligent Field Extraction** - LLM-based extraction with text chunking, keyword filtering and confidence scoring
- **Multi-Level Approvals** - Task routing to reviewers with email notifications and human validation
- **JWT Authentication & RBAC** - Secure token-based auth with role-based access control
- **Async Processing** - RabbitMQ-driven background jobs for non-blocking document processing
- **Multi-Format Support** - PDF, Word, Excel, images and more via Tika + Tesseract OCR
- **Redis Caching** - High-performance session and query caching
- **API Documentation** - Interactive Swagger UI at `/swagger-ui.html`

---

## Architecture

### System Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    User Uploads Document                        │
└────────────────┬────────────────────────────────────────────────┘
                 │
                 ▼
        ┌────────────────────┐
        │  MinIO S3 Storage  │
        └────────┬───────────┘
                 │
                 ▼
        ┌────────────────────┐
        │  RabbitMQ Event    │  (Async Processing)
        └────────┬───────────┘
                 │
                 ▼
        ┌────────────────────────────────────┐
        │  OCR Service (Tika + Tesseract)    │
        └────────┬───────────────────────────┘
                 │
                 ▼
        ┌────────────────────────────────────┐
        │  Field Extraction Service          │
        │  (Phi LLM via Ollama)              │
        │  - Chunking (1200 chars)           │
        │  - Relevance Filtering             │
        │  - Regex Fallback                  │
        └────────┬───────────────────────────┘
                 │
                 ▼
        ┌────────────────────────────────────┐
        │  Workflow Service                  │
        │  - Create Approval Tasks           │
        │  - Multi-Level Routing             │
        └────────┬───────────────────────────┘
                 │
                 ▼
        ┌────────────────────────────────────┐
        │  Approver Review & Validation      │
        │  - View Extracted Fields           │
        │  - Confidence Scores               │
        │  - Approve/Reject Decision         │
        └────────────────────────────────────┘
```

### Technology Stack

#### Core Framework
- **Java 17**
- **Spring Boot 3.3.0**
  
#### Database & Caching
- **PostgreSQL**
- **Redis**
- **Hibernate/JPA** - ORM for database access

#### Document Processing
- **Apache Tika** - Universal document text extraction
- **Tesseract 5** - OCR engine for scanned documents
- **MinIO** - S3-compatible object storage

#### Message Queue & Async
- **RabbitMQ** - Message broker for async processing
- **Spring AMQP** - RabbitMQ integration with Spring

#### AI & Machine Learning
- **Ollama** - Local LLM runtime
- **Phi 2.7B** - Lightweight LLM model (Q4_0 quantization, 1.49GB)
- **OkHttp3** - HTTP client with streaming support

#### API & Security
- **Spring Security** - Authentication and authorization
- **JWT** - Token-based authentication
- **SpringDoc OpenAPI** - Swagger/OpenAPI documentation
- **Jackson** - JSON serialization/deserialization
