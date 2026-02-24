package com.aykhedma.service;


import com.aykhedma.dto.response.*;
import com.aykhedma.dto.service.CategoryWithServicesDTO;
import com.aykhedma.dto.service.ServiceCategoryDTO;
import com.aykhedma.dto.service.ServiceTypeDTO;
import com.aykhedma.dto.service.ServicesResponse;
import com.aykhedma.model.service.ServiceCategory;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.service.RiskLevel;
import com.aykhedma.repository.ServiceCategoryRepository;
import com.aykhedma.repository.ServiceTypeRepository;
import com.aykhedma.service.ServiceManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServiceManagementServiceImpl {

    private final ServiceTypeRepository typeRepository;
    private final ServiceCategoryRepository categoryRepository;

    public List<ServiceTypeDTO> getAllTypes() {
        return typeRepository.findAll().stream()
                .map(this::mapToDTO)
                .toList();
    }

    public ServiceTypeDTO getTypeById(Long id) {
        ServiceType st = typeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service type not found"));
        return mapToDTO(st);
    }

    public ServiceTypeDTO createType(ServiceTypeDTO dto) {
        ServiceCategory category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found"));

        ServiceType type = ServiceType.builder()
                .name(dto.getName())
                .nameAr(dto.getNameAr())
                .description(dto.getDescription())
                .category(category)
                .riskLevel(dto.getRiskLevel())
                .basePrice(dto.getBasePrice())
                .defaultPriceType(dto.getDefaultPriceType())
                .estimatedDuration(dto.getEstimatedDuration())
                .build();

        typeRepository.save(type);
        return mapToDTO(type);
    }

    public ServiceTypeDTO updateType(Long id, ServiceTypeDTO dto) {
        ServiceType type = typeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Service type not found"));

        type.setName(dto.getName());
        type.setNameAr(dto.getNameAr());
        type.setDescription(dto.getDescription());
        type.setRiskLevel(dto.getRiskLevel());
        type.setBasePrice(dto.getBasePrice());
        type.setDefaultPriceType(dto.getDefaultPriceType());
        type.setEstimatedDuration(dto.getEstimatedDuration());

        if (dto.getCategoryId() != null) {
            ServiceCategory category = categoryRepository.findById(dto.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found"));
            type.setCategory(category);
        }

        typeRepository.save(type);
        return mapToDTO(type);
    }

    public void deleteType(Long id) {
        typeRepository.deleteById(id);
    }

    public long countTypes() {
        return typeRepository.countServices();
    }

    private ServiceTypeDTO mapToDTO(ServiceType st) {
        return ServiceTypeDTO.builder()
                .id(st.getId())
                .name(st.getName())
                .nameAr(st.getNameAr())
                .description(st.getDescription())
                .categoryId(st.getCategory().getId())
                .categoryName(st.getCategory().getName())
                .riskLevel(st.getRiskLevel())
                .basePrice(st.getBasePrice())
                .defaultPriceType(st.getDefaultPriceType())
                .estimatedDuration(st.getEstimatedDuration())
                .build();
    }
}