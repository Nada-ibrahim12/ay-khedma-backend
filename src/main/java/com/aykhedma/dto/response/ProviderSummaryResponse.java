package com.aykhedma.dto.response;

import com.aykhedma.model.service.PriceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderSummaryResponse {

    private Long id;
    private String name;
    private String profileImage;
    private String serviceType;
    private Double averageRating;
    private Integer completedJobs;
    private Double price;
    private PriceType priceType;
    private Double distance; // in km
    private Integer estimatedArrivalTime; // in minutes
    private boolean emergencyEnabled;
}