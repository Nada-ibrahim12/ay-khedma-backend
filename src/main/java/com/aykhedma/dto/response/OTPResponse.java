package com.aykhedma.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OTPResponse {

    private String message;
    private String phoneNumber;
    private boolean sent;
    private Integer resendAfter; // seconds
}