package com.tech.docflow.service;

import java.io.InputStream;
import java.io.IOException;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TextExtractionService {
    
    private final Tika tika = new Tika();
    
    /**
     * Extract text from any document format using Apache Tika
     * Supports: PDF, Word (DOCX), Excel (XLSX), PowerPoint, RTF, TXT, etc.
     */
    public String extractTextFromDocument(InputStream fileStream, String fileName) 
            throws IOException, TikaException {
        try {
            return tika.parseToString(fileStream);
        } catch (TikaException e) {
            throw new RuntimeException("Tika error extracting text from " + fileName + ": " + e.getMessage(), e);
        } catch (IOException e) {
            throw new RuntimeException("IO error extracting text from " + fileName + ": " + e.getMessage(), e);
        }
    }
}