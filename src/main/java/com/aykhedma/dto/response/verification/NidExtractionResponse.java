package com.aykhedma.dto.response.verification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NidExtractionResponse {
    private String nid;
    private boolean valid;
    private String photoUrl;
    private String error;
}
