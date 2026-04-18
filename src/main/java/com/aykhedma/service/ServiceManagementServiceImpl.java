package com.aykhedma.service;

import com.aykhedma.dto.response.SearchResponse;
import com.aykhedma.dto.service.ServiceCategoryDTO;
import com.aykhedma.dto.service.ServiceTypeDTO;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.mapper.ProviderMapper;
import com.aykhedma.model.service.ServiceCategory;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.user.Provider;
import com.aykhedma.repository.ProviderRepository;
import com.aykhedma.repository.ServiceCategoryRepository;
import com.aykhedma.repository.ServiceTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ServiceManagementServiceImpl {

    private final ServiceTypeRepository typeRepository;
    private final ServiceCategoryRepository categoryRepository;
    private final LocationService locationService;
    private final ProviderRepository providerRepository;
    private final ProviderMapper providerMapper;



    public List<ServiceTypeDTO> getAllTypes() {
        return typeRepository.findAll()
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    public ServiceTypeDTO getTypeById(Long id) {
        validateId(id);

        ServiceType st = typeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service type not found"));

        return mapToDTO(st);
    }

    public ServiceTypeDTO createType(ServiceTypeDTO dto) {

        validateType(dto);

        if (typeRepository.existsByNameIgnoreCase(dto.getName())) {
            throw new BadRequestException("SERVICE_TYPE_NAME_ALREADY_EXISTS");
        }

        ServiceCategory category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        ServiceType type = ServiceType.builder()
                .name(dto.getName().trim())
                .nameAr(dto.getNameAr())
                .description(dto.getDescription())
                .category(category)
                .riskLevel(dto.getRiskLevel())
                .basePrice(dto.getBasePrice())
                .defaultPriceType(dto.getDefaultPriceType())
                .estimatedDuration(dto.getEstimatedDuration())
                .build();

        return mapToDTO(typeRepository.save(type));
    }

    public ServiceTypeDTO updateType(Long id, ServiceTypeDTO dto) {

        validateId(id);

        ServiceType type = typeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Service type not found"));

        if (dto.getName() != null &&
                !dto.getName().equalsIgnoreCase(type.getName()) &&
                typeRepository.existsByNameIgnoreCase(dto.getName())) {
            throw new BadRequestException("SERVICE_TYPE_NAME_ALREADY_EXISTS");
        }

        Optional.ofNullable(dto.getName()).ifPresent(type::setName);
        Optional.ofNullable(dto.getNameAr()).ifPresent(type::setNameAr);
        Optional.ofNullable(dto.getDescription()).ifPresent(type::setDescription);
        Optional.ofNullable(dto.getRiskLevel()).ifPresent(type::setRiskLevel);
        Optional.ofNullable(dto.getBasePrice()).ifPresent(type::setBasePrice);
        Optional.ofNullable(dto.getDefaultPriceType()).ifPresent(type::setDefaultPriceType);
        Optional.ofNullable(dto.getEstimatedDuration()).ifPresent(type::setEstimatedDuration);

        if (dto.getCategoryId() != null) {
            ServiceCategory category = categoryRepository.findById(dto.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
            type.setCategory(category);
        }

        return mapToDTO(typeRepository.save(type));
    }

    public void deleteType(Long id) {
        validateId(id);

        if (!typeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Service type not found");
        }

        typeRepository.deleteById(id);
    }

    public long countTypes() {
        return typeRepository.countServices();
    }


    @Transactional(readOnly = true)
    public Page<SearchResponse> search(String keyword,
                                       Long categoryId,
                                       String categoryName,
                                       Long consumerId,
                                       Double radius,
                                       String sortBy,
                                       Pageable pageable) {

        List<SearchResponse> fullList =
                searchList(keyword, categoryId, categoryName, consumerId, radius, sortBy);

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), fullList.size());

        List<SearchResponse> pageContent =
                start >= fullList.size()
                        ? Collections.emptyList()
                        : fullList.subList(start, end);

        return new PageImpl<>(pageContent, pageable, fullList.size());
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "searchProvidersCache",
            key = "{#keyword,#categoryId,#categoryName,#consumerId,#radius,#sortBy}")
    public List<SearchResponse> searchList(String keyword,
                                           Long categoryId,
                                           String categoryName,
                                           Long consumerId,
                                           Double radius,
                                           String sortBy) {

        Page<Provider> providersPage =
                providerRepository.searchProviders(keyword, categoryId, categoryName, Pageable.unpaged());

        List<Provider> providers = providersPage.getContent();


        if (consumerId == null || radius == null) {
            return providers.stream()
                    .map(provider -> {
                        SearchResponse r = providerMapper.toSearchResponse(provider);
                        r.setDistance(null);
                        r.setEstimatedArrivalTime(null);
                        return r;
                    })
                    .collect(Collectors.toList());
        }

        List<SearchResponse> result = new ArrayList<>();

        for (Provider provider : providers) {

            if (provider.getLocation() == null) continue;

            try {
                double distance = locationService
                        .calculateDistanceBetweenConsumerAndProvider(consumerId, provider.getId())
                        .getDistanceKm();

                if (distance > radius) continue;

                SearchResponse response = providerMapper.toSearchResponse(provider);
                response.setDistance(Math.round(distance * 100.0) / 100.0);
                response.setEstimatedArrivalTime((int) ((distance / 30.0) * 60));
                response.setWithinServiceArea(distance <= provider.getServiceAreaRadius());

                result.add(response);

            } catch (Exception ex) {
                // log instead of hiding silently
                System.err.println("Distance calculation failed for provider " + provider.getId());
            }
        }

        return applySorting(result, sortBy);
    }



    private List<SearchResponse> applySorting(List<SearchResponse> list, String sortBy) {

        if (list == null || list.isEmpty()) return new ArrayList<>();

        String sort = sortBy == null ? "rating" : sortBy.toLowerCase();

        return switch (sort) {

            case "price_low" ->
                    list.stream().sorted(Comparator.comparing(SearchResponse::getPrice)).toList();

            case "price_high" ->
                    list.stream().sorted(Comparator.comparing(SearchResponse::getPrice).reversed()).toList();

            case "experience" ->
                    list.stream().sorted(Comparator.comparing(SearchResponse::getCompletedJobs).reversed()).toList();

            case "distance" ->
                    list.stream()
                            .filter(r -> r.getDistance() != null)
                            .sorted(Comparator.comparing(SearchResponse::getDistance))
                            .toList();

            default ->
                    list.stream().sorted(
                            Comparator.comparing(
                                    SearchResponse::getAverageRating,
                                    Comparator.nullsLast(Comparator.reverseOrder())
                            )
                    ).toList();
        };
    }

    // ================= VALIDATION =================

    private void validateType(ServiceTypeDTO dto) {

        if (dto == null)
            throw new BadRequestException("Request body is required");

        if (dto.getName() == null || dto.getName().isBlank())
            throw new BadRequestException("Service name is required");

        if (dto.getCategoryId() == null)
            throw new BadRequestException("Category ID is required");
    }

    private void validateId(Long id) {
        if (id == null) {
            throw new BadRequestException("ID is required");
        }
    }


    private ServiceTypeDTO mapToDTO(ServiceType st) {

        if (st.getCategory() == null) {
            throw new ResourceNotFoundException("Category missing for service type");
        }

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