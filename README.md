# DocFlow - Intelligent Document Processing System

An enterprise-grade document processing platform that combines OCR technology, AI-powered field extraction, and multi-level approval workflows to automate document handling and data extraction at scale.

---

## Key Features

- **Document Processing Pipeline** - Upload → OCR (Tesseract) → Field Extraction (Phi LLM) → Approval Workflows
- **Intelligent Field Extraction** - LLM-based extraction with text chunking, keyword filtering and confidence scoring
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

---

## Prerequisites

### System Requirements
- **Docker & Docker Compose** - For containerized services
- **Java 17+** - For local development (or use Docker)
- **Maven 3.8+** - Build tool (or use `./mvnw`)
- **WSL 2 (if on Windows)** - With 8GB allocated memory for Ollama/LLM

### Services
- **PostgreSQL 16** - Database
- **Redis 7** - Cache layer
- **RabbitMQ 3.12** - Message broker
- **MinIO** - S3-compatible storage
- **Ollama** - LLM runtime (for field extraction)

### Memory & Resources
- **WSL/Docker**: Minimum 8GB RAM for Ollama + Phi model
- **Phi Model**: ~2.0GB RAM usage
- **Ollama Startup**: ~118-125 seconds (cold start)
- **OkHttp Timeout**: 240 seconds (covers startup + inference)

---

## 🚀 Installation & Setup

### 1. Clone Repository
```bash
git clone https://github.com/babafemiolatona/docflow.git
cd docflow
```

### 2. Environment Configuration
Create a `.env` file in the project root:

```env
# Database
POSTGRES_USER=docflow
POSTGRES_PASSWORD=secure_password_here
POSTGRES_DB=docflowdb

# RabbitMQ
RABBITMQ_USER=guest
RABBITMQ_PASSWORD=guest

# MinIO
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin
MINIO_ENDPOINT=http://minio:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET_NAME=docflow-documents

# JWT
JWT_SECRET=your-super-secret-jwt-key-change-this-in-production
JWT_ACCESS_TOKEN_EXPIRATION=900000
JWT_REFRESH_TOKEN_EXPIRATION=604800000

# Server
SERVER_PORT=8080
```

### 3. Build & Run with Docker Compose

```bash
# Start services
docker compose up --build

# View logs
docker compose logs -f app

# Stop services
docker compose down

# Stop services and remove volumes
docker compose down -v
```
---

## Configuration

### Application Properties (`application.yaml`)

Key configurations you may need to adjust:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/docflowdb
    username: ${POSTGRES_USER}
    password: ${POSTGRES_PASSWORD}
  
  jpa:
    hibernate:
      ddl-auto: update  # 'create' for fresh schema, 'update' for existing
  
  data:
    redis:
      host: redis
      port: 6379
  
  rabbitmq:
    host: ${RABBITMQ_HOST}
    port: ${RABBITMQ_PORT}

server:
  port: 8080
```

### Ollama Configuration

The application connects to Ollama at `http://ollama:11434` (Docker) or `http://localhost:11434` (local):

- **Model**: Phi 2.7B quantized (Q4_0)
- **Pre-warmup**: Runs on app startup (blocks until model is ready)
- **Keep-alive**: 5-minute scheduled task prevents model unload
- **Timeout**: 240 seconds for OkHttp (covers startup + 60-80s inference)

---

### Key Endpoints

#### Authentication
- `POST /api/v1/auth/register` - Register new user
- `POST /api/v1/auth/login` - Login and get JWT token

#### Document Management
- `POST /api/v1/documents/upload` - Upload document for processing
- `GET /api/v1/documents/{id}` - Get document details
- `GET /api/v1/documents/{id}/ocr-result` - Get OCR text result

#### Field Extraction
- `GET /api/v1/documents/{id}/fields` - Get extracted fields with confidence scores

#### Approvals
- `GET /api/v1/approvals/my-tasks` - Get pending approval tasks
- `GET /api/v1/approvals/{taskId}` - Get approval task details
- `POST /api/v1/approvals/{taskId}/approve` - Approve with optional comments
- `POST /api/v1/approvals/{taskId}/reject` - Reject with reason

---
