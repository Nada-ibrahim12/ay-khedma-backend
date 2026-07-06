package com.aykhedma.service;

import com.aykhedma.dto.response.ProviderDistanceProjection;
import com.aykhedma.dto.response.SearchResponse;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.mapper.ProviderMapper;
import com.aykhedma.model.service.PriceType;
import com.aykhedma.model.user.Provider;
import com.aykhedma.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class SearchCacheService {

    private final ProviderRepository providerRepository;
    private final ProviderMapper providerMapper;
    private final LocationService locationService;


    @Transactional(readOnly = true)
    @Cacheable(
            value = "searchProvidersCache",
            key = "#keyword + '-' + #categoryId + '-' + #categoryName + '-' + #consumerId + '-' + #radius + '-' + #sortBy + '-' + #pageable.pageNumber + '-' + #pageable.pageSize"
    )
    public List<SearchResponse> searchList(String keyword,
                                           Long categoryId,
                                           String categoryName,
                                           Long consumerId,
                                           Double radius,
                                           String sortBy,
                                           Pageable pageable) {

        System.out.println("searchList executed from DB");

        Double radiusMeters = (consumerId != null && radius != null) ? radius * 1000 : null; // Convert the search radius from kilometers to meters.

        Page<ProviderDistanceProjection> providersPage = // Retrieve all matching providers from the database.
                providerRepository.searchProvidersWithDistance(
                        keyword,
                        categoryId,
                        categoryName,
                        consumerId,
                        radiusMeters,
                        Pageable.unpaged()
                );
        // Map database projections to API response objects.
        List<SearchResponse> responses = providersPage.getContent()
                .stream()
                .map(this::map)
                .collect(Collectors.toList());

        return applySorting(responses, sortBy);
    }

    private List<SearchResponse> applySorting(List<SearchResponse> responses,
                                              String sortBy) {

        if (responses == null || responses.isEmpty()) {
            return new ArrayList<>();
        }
        // Default sorting is by rating.
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

            case "completed_jobs":
                return responses.stream()
                        .sorted(
                                Comparator.comparing(
                                        SearchResponse::getCompletedJobs,
                                        Comparator.nullsLast(Comparator.reverseOrder())
                                )
                        )
                        .collect(Collectors.toList());

            case "distance":
                return responses.stream()
                        .filter(r -> r.getDistance() != null)
                        .sorted(Comparator.comparing(SearchResponse::getDistance))
                        .collect(Collectors.toList());

            case "rating":
            default:
                return responses.stream()
                        .sorted(
                                Comparator.comparing(
                                        SearchResponse::getAverageRating,
                                        Comparator.nullsLast(Comparator.reverseOrder())
                                )
                        )
                        .collect(Collectors.toList());
        }
    }

    @Transactional(readOnly = true)
    public List<SearchResponse> topRatedNearMe(Long consumerId, Double radius) {

        if (consumerId == null) {
            throw new BadRequestException("consumerId is required");
        }

        if (radius == null || radius <= 0) {
            throw new BadRequestException("radius must be greater than 0");
        }

        double radiusMeters = radius * 1000;
        // Retrieve nearby providers ordered by rating.
        Page<ProviderDistanceProjection> result =
                providerRepository.findTopRatedNearConsumer(
                        consumerId,
                        radiusMeters,
                        Pageable.unpaged()
                );

        return result.getContent()
                .stream()//stream to do map or filter and so on
                .map(this::map)
                .toList();
    }

    private SearchResponse map(ProviderDistanceProjection p) {

        SearchResponse res = new SearchResponse();

        res.setId(p.getId());
        res.setName(p.getName());
        res.setProfileImage(p.getProfileImage());

        res.setServiceType(p.getServiceType());
        res.setServiceTypeAr(p.getServiceTypeAr());
        res.setCategoryName(p.getCategoryName());

        res.setAverageRating(p.getAverageRating());

        res.setPrice(p.getPrice());
        PriceType priceType = p.getPriceType() != null ? PriceType.valueOf(p.getPriceType()) : null;
        res.setPriceType(priceType);
        res.setPriceTypeAr(priceType != null ? priceType.getArabicLabel() : null);

        res.setServiceAreaRadius(p.getServiceAreaRadius());

        res.setAveragePunctualityRating(p.getAveragePunctualityRating());
        res.setAverageCommitmentRating(p.getAverageCommitmentRating());
        res.setAverageQualityOfWorkRating(p.getAverageQualityOfWorkRating());

        res.setArea(p.getArea());

        res.setScore(p.getScore());

        res.setDistance(p.getDistanceKm());
        res.setEstimatedArrivalTime(p.getEstimatedArrivalTime());

        return res;
    }
}