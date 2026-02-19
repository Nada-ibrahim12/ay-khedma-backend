package com.aykhedma.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface FileStorageService {

    /**
     * Store a file in the specified directory
     * @param file The file to store
     * @param directory The directory name (e.g., "profile-images", "documents")
     * @return The URL/path to access the file
     * @throws IOException If file storage fails
     */
    String storeFile(MultipartFile file, String directory) throws IOException;

    /**
     * Delete a file by its URL
     * @param fileUrl The URL of the file to delete
     */
    void deleteFile(String fileUrl);

    /**
     * Get the file URL by filename
     * @param fileName The name of the file
     * @return The URL to access the file
     */
    String getFileUrl(String fileName);

    /**
     * Validate if the file is a valid image
     * @param file The file to validate
     * @return true if valid image, false otherwise
     */
    boolean isValidImage(MultipartFile file);

    /**
     * Validate if the file is a valid document (PDF, DOC, etc.)
     * @param file The file to validate
     * @return true if valid document, false otherwise
     */
    boolean isValidDocument(MultipartFile file);

    /**
     * Get the file size in a human-readable format
     * @param file The file
     * @return Formatted size string
     */
    String getFileSize(MultipartFile file);
}