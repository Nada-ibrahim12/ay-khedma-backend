package com.aykhedma.dto.response;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.model.emergency.EmergencyRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyRequestResponse
{
    private Long id;
    private ConsumerSummaryResponse consumer;
    private String serviceType;
    private LocationDTO location;
    private Double price;
    private String description;
    private EmergencyRequestStatus status;
    private ProviderSummaryResponse selectedProvider;
    private LocalDateTime createdAt;
}
