package com.tech.docflow.models;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "extracted_fields")
public class ExtractedField {
    
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
    
    @Column(nullable = false)
    private Boolean isVerified = false;
    
    @CreationTimestamp
    @Column(updatable = false)
    private Instant extractedAt;
}