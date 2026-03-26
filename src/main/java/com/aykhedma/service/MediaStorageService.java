package com.aykhedma.service;

import com.aykhedma.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class MediaStorageService {

    @Value("${media.upload-dir:uploads/chat-media}")
    private String mediaUploadDir;

    private static final List<String> ALLOWED_MEDIA_TYPES = Arrays.asList(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp",
            "video/mp4", "video/mkv", "video/avi", "video/mov"
    );

    private static final long MAX_MEDIA_SIZE = 20 * 1024 * 1024; // 20MB

    // Store media file and return full URL
    public String storeMedia(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is empty or null");
        }

        validateMediaFile(file);

        Path uploadPath = Paths.get(mediaUploadDir).normalize();
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String originalFileName = file.getOriginalFilename();
        String fileExtension = "";
        if (originalFileName != null && originalFileName.contains(".")) {
            fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."))
                    .replaceAll("[^a-zA-Z0-9.]", "");
        }

        String fileName = UUID.randomUUID() + fileExtension;
        Path filePath = uploadPath.resolve(fileName).normalize();

        if (!filePath.startsWith(uploadPath)) {
            throw new BadRequestException("Invalid file path");
        }

        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Return full URL to satisfy entity validation
        return "http://localhost:8084/chat-media/" + fileName;
    }

    public void deleteMedia(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) return;

        try {
            String fileName = Paths.get(fileUrl).getFileName().toString();
            Path filePath = Paths.get(mediaUploadDir, fileName).normalize();
            if (filePath.startsWith(Paths.get(mediaUploadDir).normalize())) {
                Files.deleteIfExists(filePath);
            }
        } catch (IOException ignored) {}
    }

    private void validateMediaFile(MultipartFile file) {
        if (!ALLOWED_MEDIA_TYPES.contains(file.getContentType())) {
            throw new BadRequestException(
                    "Invalid media type. Allowed: images (jpg, png, gif, webp) & videos (mp4, mkv, avi, mov)"
            );
        }

        if (file.getSize() > MAX_MEDIA_SIZE) {
            throw new BadRequestException(
                    "Media size exceeds 20MB. Current: " + getFileSize(file)
            );
        }
    }

    private String getFileSize(MultipartFile file) {
        long size = file.getSize();
        if (size < 1024) return size + " B";
        else if (size < 1024 * 1024) return String.format("%.2f KB", size / 1024.0);
        else return String.format("%.2f MB", size / (1024.0 * 1024.0));
    }
}