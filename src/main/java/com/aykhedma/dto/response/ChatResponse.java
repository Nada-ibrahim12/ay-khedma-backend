package com.aykhedma.dto.response;

import com.aykhedma.model.chat.ChatResponseType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    private String sessionId;
    private List<ChatMessageResponse> messages;
    private ChatResponseType responseType;
    private String message;
    private LocalDateTime timestamp;
    private String detectedLanguage;
    private String detectedDialect;
    private String audioTranscription;

    // For provider list responses
    private List<ProviderSummaryResponse> providers;

    // For booking responses
    private BookingResponse booking;

    // For emergency responses
    private EmergencyResponse emergency;
}