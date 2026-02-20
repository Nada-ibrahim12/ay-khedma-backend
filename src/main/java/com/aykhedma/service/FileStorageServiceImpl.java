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

    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    );

    private static final List<String> ALLOWED_DOCUMENT_TYPES = Arrays.asList(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain",
            "application/rtf"
    );

    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;
    private static final long MAX_DOCUMENT_SIZE = 10 * 1024 * 1024;

    @Override
    public String storeFile(MultipartFile file, String directory) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is empty or null");
        }

        // FIXED: Correct directory validation
        if (directory.equals("profile-images")) {
            validateImageFile(file);
        } else if (directory.equals("documents")) {
            validateDocumentFile(file);
        } else {
            throw new BadRequestException("Invalid directory: " + directory);
        }

        Path uploadPath = Paths.get(uploadDir, directory).normalize();
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String originalFileName = file.getOriginalFilename();
        String fileExtension = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
            // Sanitize extension
            fileExtension = fileExtension.replaceAll("[^a-zA-Z0-9.]", "");
        }

        String fileName = UUID.randomUUID().toString() + fileExtension;
        Path filePath = uploadPath.resolve(fileName).normalize();

        // Ensure the resolved path is still within the upload directory
        if (!filePath.startsWith(uploadPath)) {
            throw new BadRequestException("Invalid file path");
        }

        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return "/files/" + directory + "/" + fileName;
    }

    @Override
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return;
        }

        try {
            String[] parts = fileUrl.split("/");
            if (parts.length >= 4) {
                String directory = parts[2];
                String fileName = parts[3];

                // Only allow specific directories
                if (!directory.equals("profile-images") && !directory.equals("documents")) {
                    return;
                }

                // Sanitize filename
                fileName = Paths.get(fileName).getFileName().toString();
                Path filePath = Paths.get(uploadDir, directory, fileName).normalize();

                // Ensure the path is still within the upload directory
                Path uploadPath = Paths.get(uploadDir).normalize();
                if (filePath.startsWith(uploadPath)) {
                    Files.deleteIfExists(filePath);
                }
            }
        } catch (IOException e) {
            // Ignore deletion errors
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
        } else {
            return String.format("%.2f MB", size / (1024.0 * 1024.0));
        }
    }

    private void validateImageFile(MultipartFile file) {
        if (!isValidImage(file)) {
            throw new BadRequestException("Invalid image type. Allowed types: JPEG, PNG, GIF, WEBP");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new BadRequestException("Image size exceeds maximum limit of 5MB. Current size: " + getFileSize(file));
        }
    }

    private void validateDocumentFile(MultipartFile file) {
        if (!isValidDocument(file)) {
            throw new BadRequestException("Invalid document type. Allowed types: PDF, DOC, DOCX, XLS, XLSX, TXT, RTF");
        }
        if (file.getSize() > MAX_DOCUMENT_SIZE) {
            throw new BadRequestException("Document size exceeds maximum limit of 10MB. Current size: " + getFileSize(file));
        }
    }
}