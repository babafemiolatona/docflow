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
