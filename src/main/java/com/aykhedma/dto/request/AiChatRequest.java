package com.aykhedma.dto.request;

import com.aykhedma.dto.location.LocationDTO;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.web.multipart.MultipartFile;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest {

    private String message;
    
    private MultipartFile voiceNote;

    private String sessionId;

    private Long providerId;

    private Long serviceTypeId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate requestedDate;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime requestedTime;

    private LocationDTO location;
}