package com.aykhedma.dto.response;

import com.aykhedma.dto.location.LocationDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DistanceResponse {
    private Long consumerId;
    private Long providerId;
    private Long secondProviderId;
    private Double distanceKm;
    private LocationDTO consumerLocation;
    private LocationDTO providerLocation;
    private LocationDTO secondProviderLocation;
    private Boolean withinServiceArea;
    private Double providerServiceArea;
    private String message;

    public String getFormattedDistance() {
        if (distanceKm < 1) {
            return (distanceKm * 1000) + " meters";
        }
        return distanceKm + " km";
    }
}