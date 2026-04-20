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
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ocr")
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