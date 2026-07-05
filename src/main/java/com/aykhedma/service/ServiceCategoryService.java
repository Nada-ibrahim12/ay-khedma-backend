package com.aykhedma.service;

import com.aykhedma.dto.service.ServiceCategoryDTO;
import com.aykhedma.dto.service.ServiceTypeDTO;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.model.service.ServiceCategory;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.repository.ProviderRepository;
import com.aykhedma.repository.ServiceCategoryRepository;
import com.aykhedma.repository.ServiceTypeRepository;
import com.cloudinary.Cloudinary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ServiceCategoryService {

    private final ServiceCategoryRepository categoryRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final ProviderRepository providerRepository;
    private final Cloudinary cloudinary;
    private final FileStorageService fileStorageService;

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

    public ServiceCategoryDTO createCategory(ServiceCategoryDTO dto, MultipartFile image) {

        validateCategoryRequest(dto);

        if (categoryRepository.existsByName(dto.getName().trim())) {
            throw new BadRequestException("Category name already exists");
        }

        String imageUrl = null;

        try {
            if (image != null && !image.isEmpty()) {
                imageUrl = fileStorageService.storeFile(image, "category-images");
            }
        } catch (IOException e) {
            throw new BadRequestException("Failed to upload category image");
        }

        ServiceCategory category = ServiceCategory.builder()
                .name(dto.getName().trim())
                .nameAr(dto.getNameAr())
                .description(dto.getDescription())
                .imageUrl(imageUrl)
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

    public ServiceCategoryDTO updateCategory(
            Long id,
            ServiceCategoryDTO dto,
            MultipartFile image) {

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

        if (image != null && !image.isEmpty()) {
            String oldImageUrl = category.getImageUrl();
            try {
                String imageUrl = fileStorageService.storeFile(image, "category-images");
                category.setImageUrl(imageUrl);
            } catch (IOException e) {
                throw new BadRequestException("Failed to upload category image");
            }

            // Clean up the old image now that the new one uploaded successfully
            if (oldImageUrl != null && !oldImageUrl.isBlank()) {
                fileStorageService.deleteFile(oldImageUrl);
            }
        }

        ServiceCategory updated = categoryRepository.save(category);

        return mapToDTO(updated);
    }
    @Transactional
    public void deleteCategory(Long id) {

        if (id == null) {
            throw new BadRequestException("Category id is required");
        }

        ServiceCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        List<ServiceType> types = category.getServiceTypes();

        for (ServiceType type : types) {

            boolean hasProviders =
                    providerRepository.existsByServiceTypeId(type.getId());

            if (hasProviders) {
                throw new BadRequestException(
                        "Cannot delete category because it has providers using its services"
                );
            }
        }

        serviceTypeRepository.deleteAll(types);

        categoryRepository.delete(category);
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
                .imageUrl(category.getImageUrl())
                .serviceTypes(types)
                .build();
    }
    @Transactional
    public void deleteCategoryImage(Long id) {

        if (id == null) {
            throw new BadRequestException("Category id is required");
        }

        ServiceCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        if (category.getImageUrl() == null || category.getImageUrl().isBlank()) {
            throw new BadRequestException("Category has no image");
        }

        // Delete from Cloudinary/storage
        fileStorageService.deleteFile(category.getImageUrl());

        // Remove image URL from database
        category.setImageUrl(null);

        categoryRepository.save(category);
    }}