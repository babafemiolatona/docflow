package com.tech.docflow.dto;

import com.tech.docflow.models.OcrJobStatus;
import java.time.Instant;
import lombok.*;

@Getter
@Setter
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