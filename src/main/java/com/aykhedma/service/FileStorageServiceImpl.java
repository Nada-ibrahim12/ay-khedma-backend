package com.aykhedma.service;

import com.aykhedma.exception.BadRequestException;
import com.aykhedma.service.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    // Allowed image types
    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    );

    // Allowed document types
    private static final List<String> ALLOWED_DOCUMENT_TYPES = Arrays.asList(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain",
            "application/rtf"
    );

    // Max file size: 5MB for images, 10MB for documents
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final long MAX_DOCUMENT_SIZE = 10 * 1024 * 1024; // 10MB

    @Override
    public String storeFile(MultipartFile file, String directory) throws IOException {
        // Validate file
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is empty or null");
        }

        // Validate based on directory
        if (directory.equals("profile-images") || directory.equals("profile-images")) {
            validateImageFile(file);
        } else if (directory.equals("documents")) {
            validateDocumentFile(file);
        } else {
            throw new BadRequestException("Invalid directory: " + directory);
        }

        // Create directory if it doesn't exist
        Path uploadPath = Paths.get(uploadDir, directory);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Generate unique filename to prevent overwriting
        String originalFileName = file.getOriginalFilename();
        String fileExtension = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
        }

        String fileName = UUID.randomUUID().toString() + fileExtension;
        Path filePath = uploadPath.resolve(fileName);

        // Copy file
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Return relative path for database storage
        return "/files/" + directory + "/" + fileName;
    }

    @Override
    public void deleteFile(String fileUrl) {
        try {
            if (fileUrl != null && !fileUrl.isEmpty()) {
                // Parse URL to get directory and filename
                // URL format: /files/profile-images/filename.jpg
                String[] parts = fileUrl.split("/");
                if (parts.length >= 4) {
                    String directory = parts[2];
                    String fileName = parts[3];

                    Path filePath = Paths.get(uploadDir, directory, fileName);
                    Files.deleteIfExists(filePath);
                }
            }
        } catch (IOException e) {
            // Log error but don't throw exception
            System.err.println("Could not delete file: " + e.getMessage());
        }
    }

    @Override
    public String getFileUrl(String fileName) {
        return "/files/profile-images/" + fileName;
    }

    @Override
    public boolean isValidImage(MultipartFile file) {
        if (file == null || file.isEmpty()) return false;

        String contentType = file.getContentType();
        return contentType != null && ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase());
    }

    @Override
    public boolean isValidDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) return false;

        String contentType = file.getContentType();
        return contentType != null && ALLOWED_DOCUMENT_TYPES.contains(contentType.toLowerCase());
    }

    @Override
    public String getFileSize(MultipartFile file) {
        if (file == null) return "0 B";

        long size = file.getSize();
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }

    // Private validation methods
    private void validateImageFile(MultipartFile file) {
        // Check file type
        if (!isValidImage(file)) {
            throw new BadRequestException("Invalid image type. Allowed types: JPEG, PNG, GIF, WEBP");
        }

        // Check file size
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BadRequestException("Image size exceeds maximum limit of 5MB. Current size: " + getFileSize(file));
        }
    }

    private void validateDocumentFile(MultipartFile file) {
        // Check file type
        if (!isValidDocument(file)) {
            throw new BadRequestException("Invalid document type. Allowed types: PDF, DOC, DOCX, XLS, XLSX, TXT, RTF");
        }

        // Check file size
        if (file.getSize() > MAX_DOCUMENT_SIZE) {
            throw new BadRequestException("Document size exceeds maximum limit of 10MB. Current size: " + getFileSize(file));
        }
    }
}