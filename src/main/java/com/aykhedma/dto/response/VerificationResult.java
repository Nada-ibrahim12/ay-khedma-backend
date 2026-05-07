package com.aykhedma.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationResult {
    private boolean faceMatch;
    private double faceMatchScore;
    private boolean idMatch;
    private String extractedId;
    private String message;
}
