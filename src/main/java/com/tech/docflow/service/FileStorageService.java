package com.tech.docflow.service;

import java.io.InputStream;

public interface FileStorageService {

    String uploadFile(String fileName, InputStream fileContent, String contentType, long fileSize);
    
    InputStream downloadFile(String filePath);
    
    void deleteFile(String filePath);
    
    boolean fileExists(String filePath);
    
    String getFileUrl(String filePath);

}