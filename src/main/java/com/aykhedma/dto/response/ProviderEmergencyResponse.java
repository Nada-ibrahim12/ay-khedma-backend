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
public class ProviderEmergencyResponse {

    private Long id;
    private Long providerId;
    private String providerName;
    private String providerImage;
    private Double proposedPrice;
    private Integer estimatedArrivalTime;
    private Double distance;
    private String notes;
    private LocalDateTime responseTime;
    private boolean accepted;
}