package com.tech.docflow.service;

import io.minio.*;
import io.minio.errors.MinioException;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnProperty(name = "file.storage.type", havingValue = "minio")
public class MinioFileStorageService implements FileStorageService {

    private final MinioClient minioClient;
    private final String bucketName;
    private final String endpoint;

    public MinioFileStorageService(
            @Value("${file.storage.minio.endpoint}") String endpoint,
            @Value("${file.storage.minio.access-key}") String accessKey,
            @Value("${file.storage.minio.secret-key}") String secretKey,
            @Value("${file.storage.minio.bucket-name}") String bucketName,
            @Value("${file.storage.minio.region:us-east-1}") String region) {
        
        this.endpoint = endpoint;
        this.bucketName = bucketName;
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize MinIO bucket: " + e.getMessage(), e);
        }
    }

    @Override
    public String uploadFile(String fileName, InputStream fileContent, String contentType, long fileSize) {
        try {
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            String objectName = String.format("documents/%s/%s", timestamp, fileName);

            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(fileContent, fileSize, -1)
                    .contentType(contentType)
                    .build()
            );

            return objectName;
        } catch (MinioException e) {
            throw new RuntimeException("MinIO error during file upload: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Error uploading file to MinIO: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream downloadFile(String filePath) {
        try {
            return minioClient.getObject(
                GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(filePath)
                    .build()
            );
        } catch (MinioException e) {
            throw new RuntimeException("MinIO error during file download: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Error downloading file from MinIO: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteFile(String filePath) {
        try {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(filePath)
                    .build()
            );
        } catch (MinioException e) {
            throw new RuntimeException("MinIO error during file deletion: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Error deleting file from MinIO: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean fileExists(String filePath) {
        try {
            minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(filePath)
                    .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getFileUrl(String filePath) {
        try {
            String presignedUrl = minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucketName)
                    .object(filePath)
                    .expiry(24, TimeUnit.HOURS)
                    .build()
            );
            
            return presignedUrl.replace("http://minio:9000", "http://localhost:9000");
        } catch (Exception e) {
            throw new RuntimeException("Error generating file URL: " + e.getMessage(), e);
        }
    }
}