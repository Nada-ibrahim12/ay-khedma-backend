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
public class ProviderResponseResponse
{
    private Long id;
    private ProviderSummaryResponse provider;
    private EmergencyRequestResponse emergencyRequest;
    private Integer estimatedArrivalTime; // In minutes
    private Double distance; // In KM
    private Double proposedPrice;
    private String notes;
    private LocalDateTime responseTime;
}
