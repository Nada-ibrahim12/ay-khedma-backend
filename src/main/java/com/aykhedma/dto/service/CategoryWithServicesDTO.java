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
public class CategoryWithServicesDTO {
    private ServiceCategoryDTO category;
    private List<ServiceTypeDTO> services;
}