package com.aykhedma.dto.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServicesResponse {
    private List<ServiceCategoryDTO> categories;
    private List<ServiceTypeDTO> services;
    private long totalCategories;
    private long totalServices;
}