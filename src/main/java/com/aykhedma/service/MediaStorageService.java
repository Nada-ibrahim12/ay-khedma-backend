package com.aykhedma.service;

import com.aykhedma.exception.BadRequestException;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MediaStorageService {

    private final Cloudinary cloudinary;

    private static final List<String> ALLOWED_MEDIA_TYPES = Arrays.asList(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp",

            "video/mp4", "video/mkv", "video/avi", "video/mov",
            "audio/mpeg", "audio/wav", "audio/ogg", "audio/webm","audio/m4a",
            "audio/mp4",
            "audio/x-m4a",
            "audio/aac",

            // documents
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain"
    );

    private static final long MAX_MEDIA_SIZE = 20 * 1024 * 1024; // 20MB

    public String storeMedia(MultipartFile file, String roomId) throws IOException {

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File is empty or null");
        }

        validateMediaFile(file);

        Map uploadResult = cloudinary.uploader().upload(
                file.getBytes(),
                ObjectUtils.asMap(
                        "resource_type", "auto",
                        "folder", "chat-media/" + roomId
                )
        );

        return uploadResult.get("secure_url").toString();
    }

    private void validateMediaFile(MultipartFile file) {

        String contentType = file.getContentType();

        if (contentType == null || !ALLOWED_MEDIA_TYPES.contains(contentType)) {
            throw new BadRequestException(
                    "Invalid file type. Allowed: images, videos, audio, documents"
            );
        }

        if (file.getSize() > MAX_MEDIA_SIZE) {
            throw new BadRequestException(
                    "File size exceeds 20MB"
            );
        }
    }
}