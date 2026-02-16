package com.aykhedma.dto.service;

import com.aykhedma.model.service.PriceType;
import com.aykhedma.model.service.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceTypeDTO {
    private Long id;
    private String name;
    private String nameAr;
    private String description;
    private Long categoryId;
    private String categoryName;
    private RiskLevel riskLevel;
    private Double basePrice;
    private PriceType defaultPriceType;
    private Integer estimatedDuration;
}