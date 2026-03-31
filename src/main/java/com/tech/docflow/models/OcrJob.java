package com.tech.docflow.models;

import lombok.*;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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
