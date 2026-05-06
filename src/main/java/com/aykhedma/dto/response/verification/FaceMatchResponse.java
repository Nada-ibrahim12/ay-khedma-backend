package com.aykhedma.dto.response.verification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FaceMatchResponse {
    private boolean match;
    private double distance;
    private double threshold;
    private String error;
}
