package com.aykhedma.dto.request;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DraftMessageRequest {
    private Long bookingId;
    private String customMessage; // the current content of the draft
}