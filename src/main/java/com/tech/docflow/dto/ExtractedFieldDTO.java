package com.tech.docflow.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedFieldDTO {
    private Long id;
    private String fieldName;
    private String fieldValue;
    private Double confidence;
    private Boolean isVerified;
}