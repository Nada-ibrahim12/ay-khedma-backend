package com.aykhedma.service;

import com.aykhedma.dto.service.ServiceCategoryDTO;
import com.aykhedma.dto.service.ServiceTypeDTO;
import com.aykhedma.model.service.ServiceCategory;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.repository.ServiceCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceCategoryService {

    private final ServiceCategoryRepository categoryRepository;

    public List<ServiceCategoryDTO> getAllCategories() {
        return categoryRepository.findAllWithServiceTypes().stream()
                .map(this::mapToDTO)
                .toList();
    }

    public ServiceCategoryDTO getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .map(this::mapToDTO)
                .orElseThrow(() -> new RuntimeException("Category not found"));
    }

    public ServiceCategoryDTO createCategory(ServiceCategoryDTO dto) {

        ServiceCategory category = ServiceCategory.builder()
                .name(dto.getName())
                .nameAr(dto.getNameAr())
                .description(dto.getDescription())
                .build();

        if (dto.getServiceTypes() != null) {
            dto.getServiceTypes().forEach(stDto -> {

                ServiceType serviceType = ServiceType.builder()
                        .name(stDto.getName())
                        .nameAr(stDto.getNameAr())
                        .description(stDto.getDescription())
                        .riskLevel(stDto.getRiskLevel())
                        .basePrice(stDto.getBasePrice())
                        .defaultPriceType(stDto.getDefaultPriceType())
                        .estimatedDuration(stDto.getEstimatedDuration())
                        .category(category) // VERY IMPORTANT
                        .build();

                category.getServiceTypes().add(serviceType);
            });
        }

        categoryRepository.save(category);

        return mapToDTO(category);
    }

    public ServiceCategoryDTO updateCategory(Long id, ServiceCategoryDTO dto) {
        ServiceCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));
        category.setName(dto.getName());
        category.setNameAr(dto.getNameAr());
        category.setDescription(dto.getDescription());
        categoryRepository.save(category);
        return mapToDTO(category);
    }

    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
    }

    public long countCategories() {
        return categoryRepository.countCategories();
    }

    private ServiceCategoryDTO mapToDTO(ServiceCategory category) {
        List<ServiceTypeDTO> types = category.getServiceTypes().stream()
                .map(st -> ServiceTypeDTO.builder()
                        .id(st.getId())
                        .name(st.getName())
                        .nameAr(st.getNameAr())
                        .description(st.getDescription())
                        .categoryId(category.getId())
                        .categoryName(category.getName())
                        .riskLevel(st.getRiskLevel())
                        .basePrice(st.getBasePrice())
                        .defaultPriceType(st.getDefaultPriceType())
                        .estimatedDuration(st.getEstimatedDuration())
                        .build())
                .toList();

        return ServiceCategoryDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .nameAr(category.getNameAr())
                .serviceTypes(types)
                .build();
    }
}