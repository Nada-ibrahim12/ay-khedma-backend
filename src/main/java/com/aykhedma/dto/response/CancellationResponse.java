package com.aykhedma.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancellationResponse {
    private Long bookingId;
    private LocalDateTime cancelledAt;
    private String cancellationReason;
    private String cancelledBy; // 'P' or 'C'
    private String otherPartyName;
}
