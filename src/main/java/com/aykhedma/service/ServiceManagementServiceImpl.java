package com.aykhedma.service;


import com.aykhedma.dto.response.*;
import com.aykhedma.dto.service.CategoryWithServicesDTO;
import com.aykhedma.dto.service.ServiceCategoryDTO;
import com.aykhedma.dto.service.ServiceTypeDTO;
import com.aykhedma.dto.service.ServicesResponse;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.mapper.ProviderMapper;
import com.aykhedma.model.service.ServiceCategory;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.service.RiskLevel;
import com.aykhedma.model.user.Provider;
import com.aykhedma.repository.ProviderRepository;
import com.aykhedma.repository.ServiceCategoryRepository;
import com.aykhedma.repository.ServiceTypeRepository;
import com.aykhedma.service.ServiceManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServiceManagementServiceImpl {

    private final ServiceTypeRepository typeRepository;
    private final ServiceCategoryRepository categoryRepository;
    private final LocationService locationService;
    private final ProviderRepository providerRepository;
    private final ProviderMapper providerMapper;

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


    @Transactional(readOnly = true)
    public Page<SearchResponse> search(String keyword,
                                       Long categoryId,
                                       String categoryName,
                                       Long consumerId,
                                       Double radius,
                                       String sortBy,
                                       Pageable pageable) {

        List<SearchResponse> fullList = searchList(keyword, categoryId, categoryName, consumerId, radius, sortBy);


        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), fullList.size());

        List<SearchResponse> pageContent =
                start >= fullList.size() ? Collections.emptyList() : fullList.subList(start, end);

        return new PageImpl<>(pageContent, pageable, fullList.size());
    }
    @Transactional(readOnly = true)
    @Cacheable(value = "searchProvidersCache", key = "{#keyword,#categoryId,#categoryName,#consumerId,#radius,#sortBy}")
    public List<SearchResponse> searchList(String keyword,
                                           Long categoryId,
                                           String categoryName,
                                           Long consumerId,
                                           Double radius,
                                           String sortBy) {


        Page<Provider> providersPage = providerRepository.searchProviders(keyword, categoryId, categoryName, Pageable.unpaged());

        if (consumerId == null || radius == null) {
            List<SearchResponse> responses = providersPage.getContent()
                    .stream()
                    .map(provider -> {
                        SearchResponse response = providerMapper.toSearchResponse(provider);
                        response.setDistance(null);
                        response.setEstimatedArrivalTime(null);
                        return response;
                    })
                    .collect(Collectors.toList());

            return applySorting(responses, sortBy, null);
        }

        try {
            List<SearchResponse> filteredList = providersPage.getContent()
                    .stream()
                    .filter(provider -> provider.getLocation() != null)
                    .map(provider -> {
                        try {
                            double distance = locationService
                                    .calculateDistanceBetweenConsumerAndProvider(consumerId, provider.getId())
                                    .getDistanceKm();

                            if (distance > radius) return null;

                            SearchResponse response = providerMapper.toSearchResponse(provider);
                            response.setDistance(Math.round(distance * 100.0) / 100.0);
                            response.setEstimatedArrivalTime((int) Math.round((distance / 30.0) * 60));
                            response.setWithinServiceArea(distance <= provider.getServiceAreaRadius());

                            return response;
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            return applySorting(filteredList, sortBy, consumerId);

        } catch (ResourceNotFoundException e) {

            List<SearchResponse> responses = providersPage.getContent()
                    .stream()
                    .map(provider -> {
                        SearchResponse response = providerMapper.toSearchResponse(provider);
                        response.setDistance(null);
                        response.setEstimatedArrivalTime(null);
                        return response;
                    })
                    .collect(Collectors.toList());

            return applySorting(responses, sortBy, null);
        }
    }

    private List<SearchResponse> applySorting(List<SearchResponse> responses, String sortBy, Long consumerId) {
        if (responses == null || responses.isEmpty()) {
            return new ArrayList<>();
        }

        String sortField = sortBy != null ? sortBy.toLowerCase() : "rating";

        switch (sortField) {
            case "price_low":
                return responses.stream()
                        .sorted(Comparator.comparing(SearchResponse::getPrice))
                        .collect(Collectors.toList());

            case "price_high":
                return responses.stream()
                        .sorted(Comparator.comparing(SearchResponse::getPrice).reversed())
                        .collect(Collectors.toList());

            case "experience":
                return responses.stream()
                        .sorted(Comparator.comparing(SearchResponse::getCompletedJobs).reversed())
                        .collect(Collectors.toList());

            case "distance":
                return responses.stream()
                        .filter(r -> r.getDistance() != null)
                        .sorted(Comparator.comparing(SearchResponse::getDistance))
                        .collect(Collectors.toList());

            case "rating":
            default:
                return responses.stream()
                        .sorted(Comparator.comparing(SearchResponse::getAverageRating,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .collect(Collectors.toList());
        }
    }
}