package com.aykhedma.service;

import com.aykhedma.dto.service.ServiceCategoryDTO;
import com.aykhedma.dto.service.ServiceTypeDTO;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.model.service.ServiceCategory;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.repository.ServiceCategoryRepository;
import com.aykhedma.repository.ServiceTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ServiceCategoryService {

    private final ServiceCategoryRepository categoryRepository;
    private final ServiceTypeRepository serviceTypeRepository;

    public List<ServiceCategoryDTO> getAllCategories() {
        return categoryRepository.findAllWithServiceTypes()
                .stream()
                .map(this::mapToDTO)
                .toList();
    }


    public ServiceCategoryDTO getCategoryById(Long id) {

        if (id == null) {
            throw new BadRequestException("Category id is required");
        }

        ServiceCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        return mapToDTO(category);
    }

    public ServiceCategoryDTO createCategory(ServiceCategoryDTO dto) {

        validateCategoryRequest(dto);

        if (categoryRepository.existsByName(dto.getName().trim())) {
            throw new BadRequestException("Category name already exists");
        }

        ServiceCategory category = ServiceCategory.builder()
                .name(dto.getName().trim())
                .nameAr(dto.getNameAr())
                .description(dto.getDescription())
                .build();

        if (dto.getServiceTypes() != null && !dto.getServiceTypes().isEmpty()) {
            dto.getServiceTypes().forEach(stDto -> {

                validateServiceType(stDto);

                if (serviceTypeRepository.existsByName(stDto.getName().trim())) {
                    throw new BadRequestException(
                            "Service type already exists: " + stDto.getName()
                    );
                }

                ServiceType serviceType = ServiceType.builder()
                        .name(stDto.getName().trim())
                        .nameAr(stDto.getNameAr())
                        .description(stDto.getDescription())
                        .riskLevel(stDto.getRiskLevel())
                        .basePrice(stDto.getBasePrice())
                        .defaultPriceType(stDto.getDefaultPriceType())
                        .estimatedDuration(stDto.getEstimatedDuration())
                        .category(category)
                        .build();

                category.getServiceTypes().add(serviceType);
            });
        }

        ServiceCategory saved = categoryRepository.save(category);
        return mapToDTO(saved);
    }

    public ServiceCategoryDTO updateCategory(Long id, ServiceCategoryDTO dto) {

        if (id == null) {
            throw new BadRequestException("Category id is required");
        }

        ServiceCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        if (dto.getName() != null && !dto.getName().isBlank()) {

            String newName = dto.getName().trim();

            if (categoryRepository.existsByName(newName)
                    && !category.getName().equalsIgnoreCase(newName)) {
                throw new BadRequestException("Category name already exists");
            }

            category.setName(newName);
        }

        if (dto.getNameAr() != null) {
            category.setNameAr(dto.getNameAr());
        }

        if (dto.getDescription() != null) {
            category.setDescription(dto.getDescription());
        }

        return mapToDTO(categoryRepository.save(category));
    }

    public void deleteCategory(Long id) {

        if (id == null) {
            throw new BadRequestException("Category id is required");
        }

        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Category not found");
        }

        categoryRepository.deleteById(id);
    }


    public long countCategories() {
        return categoryRepository.countCategories();
    }

    private void validateCategoryRequest(ServiceCategoryDTO dto) {

        if (dto == null) {
            throw new BadRequestException("Request body is required");
        }

        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BadRequestException("Category name is required");
        }
    }

    private void validateServiceType(ServiceTypeDTO dto) {

        if (dto == null) {
            throw new BadRequestException("Service type is required");
        }

        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BadRequestException("Service type name is required");
        }

        if (dto.getBasePrice() != null && dto.getBasePrice() < 0) {
            throw new BadRequestException("Base price cannot be negative");
        }

        if (dto.getEstimatedDuration() != null && dto.getEstimatedDuration() <= 0) {
            throw new BadRequestException("Estimated duration must be greater than 0");
        }
    }


    private ServiceCategoryDTO mapToDTO(ServiceCategory category) {

        List<ServiceTypeDTO> types = category.getServiceTypes()
                .stream()
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
                .description(category.getDescription())
                .serviceTypes(types)
                .build();
    }
}