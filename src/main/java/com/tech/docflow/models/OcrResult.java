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