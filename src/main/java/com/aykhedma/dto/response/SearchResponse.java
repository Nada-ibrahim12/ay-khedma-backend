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
public class SearchResponse {
    private Long id;
    private String name;
    private String profileImage;
    private String serviceType;
    private String serviceTypeAr;
    private String categoryName;
    private Double averageRating;
    private Integer completedJobs;
    private Double price;
    private PriceType priceType;
    private Double distance;
    private Integer estimatedArrivalTime;
    private boolean emergencyEnabled;
    private Double serviceAreaRadius;
    private boolean withinServiceArea;
    private String serviceArea;
    private String bio;

    public String getFormattedDistance() {
        if (distance == null) return null;
        return distance < 1 ?
                Math.round(distance * 1000) + " m" :
                String.format("%.1f km", distance);
    }

    public String getFormattedArrivalTime() {
        if (estimatedArrivalTime == null) return null;
        if (estimatedArrivalTime < 60) return estimatedArrivalTime + " min";
        return (estimatedArrivalTime / 60) + "h " + (estimatedArrivalTime % 60) + "min";
    }
}