package com.aykhedma.dto.response;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.model.emergency.EmergencyStatus;
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
public class EmergencyResponse {

    private Long id;
    private String serviceType;
    private LocationDTO location;
    private EmergencyStatus status;
    private Double emergencyFeeMultiplier;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private List<ProviderEmergencyResponse> providerResponses;
    private ProviderSummaryResponse selectedProvider;
}