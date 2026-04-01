package com.tech.docflow.dto;

import java.time.Instant;
import lombok.*;

@Getter
@Setter
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