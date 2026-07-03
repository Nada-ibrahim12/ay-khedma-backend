package com.aykhedma.mapper;

import com.aykhedma.dto.response.AdminProviderResponse;
import com.aykhedma.dto.response.CancellationResponse;
import com.aykhedma.dto.response.ProviderResponse;
import com.aykhedma.dto.response.ProviderSummaryResponse;
import com.aykhedma.dto.response.SearchResponse;
import com.aykhedma.model.booking.BookingStatus;
import com.aykhedma.model.user.Provider;
import org.mapstruct.*;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, uses = { ScheduleMapper.class })
public interface ProviderMapper {

    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "email", target = "email")
    @Mapping(source = "phoneNumber", target = "phoneNumber")
    @Mapping(source = "profileImage", target = "profileImage")
    @Mapping(source = "bio", target = "bio")
    @Mapping(source = "yearsOfExperience", target = "yearsOfExperience")
    @Mapping(source = "nationalIdFrontImage", target = "nationalIdFrontImage")
    @Mapping(source = "nationalIdBackImage", target = "nationalIdBackImage")
    @Mapping(source = "selfieImage", target = "selfieImage")
    @Mapping(source = "verificationStatus", target = "verificationStatus")
    @Mapping(source = "completedJobs", target = "completedJobs")
    @Mapping(source = "totalBookings", target = "totalBookings")
    @Mapping(source = "cancelledBookings", target = "cancelledBookings")
    @Mapping(source = "serviceType.name", target = "serviceType")
    @Mapping(source = "serviceType.nameAr", target = "serviceTypeAr")
    @Mapping(source = "serviceType.category.nameAr", target = "serviceCategoryAr")
    @Mapping(source = "serviceType.category.name", target = "serviceCategory")
    @Mapping(source = "serviceType.id", target = "serviceTypeId")
    @Mapping(source = "worksAt", target = "worksAt")
    @Mapping(source = "workLocation", target = "workLocation")
    @Mapping(source = "location", target = "location")
    @Mapping(source = "emergencyEnabled", target = "emergencyEnabled")
    @Mapping(source = "averagePunctualityRating", target = "averagePunctualityRating")
    @Mapping(source = "averageCommitmentRating", target = "averageCommitmentRating")
    @Mapping(source = "averageQualityOfWorkRating", target = "averageQualityOfWorkRating")
    @Mapping(source = "averageRating", target = "averageRating")
    @Mapping(source = "price", target = "price")
    @Mapping(source = "priceType", target = "priceType")
    @Mapping(target = "priceTypeAr", expression = "java(provider.getPriceType().getArabicLabel())")
    @Mapping(source = "averageTime", target = "averageTime")
    @Mapping(source = "acceptanceRate", target = "acceptanceRate")
    @Mapping(source = "schedule", target = "schedule")
    @Mapping(target = "averageJobs", expression = "java(provider.getAverageJobs())")
    @Mapping(target = "documents", ignore = true)
    @Mapping(source = "serviceAreaRadius", target = "serviceAreaRadius")
    @Mapping(source = "location.area", target = "area")
    @Mapping(source = "rejectionReason", target = "rejectionReason")
    @Mapping(source = "averageInteractionRating", target = "averageInteractionRating")
    @Mapping(source = "interactionRatingCount", target = "interactionRatingCount")
    @Mapping(source = "cancellationRate", target = "cancellationRate")
    @Mapping(target = "cancellationHistory", expression = "java(mapCancellationHistory(provider.getBookings()))")
    ProviderResponse toProviderResponse(Provider provider);

    @Named("providerSummary")
    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "profileImage", target = "profileImage")
    @Mapping(source = "serviceType.name", target = "serviceType")
    @Mapping(source = "serviceType.nameAr", target = "serviceTypeAr")
    @Mapping(source = "averageRating", target = "averageRating")
    @Mapping(source = "price", target = "price")
    @Mapping(source = "priceType", target = "priceType")
    @Mapping(target = "priceTypeAr", expression = "java(provider.getPriceType().getArabicLabel())")
    @Mapping(source = "location", target = "location")
    @Mapping(target = "distance", ignore = true)
    @Mapping(target = "estimatedArrivalTime", ignore = true)
    @Mapping(source = "location.area", target = "area")
    @Mapping(source = "cancellationRate", target = "cancellationRate")
    ProviderSummaryResponse toProviderSummaryResponse(Provider provider);

    @Mapping(source = "serviceType.name", target = "serviceType")
    @Mapping(source = "serviceType.nameAr", target = "serviceTypeAr")
    @Mapping(source = "serviceType.category.name", target = "categoryName")
    @Mapping(source = "serviceAreaRadius", target = "serviceAreaRadius")
    @Mapping(source = "location.area", target = "area")
    @Mapping(target = "distance", ignore = true)
    @Mapping(target = "estimatedArrivalTime", ignore = true)
    @Mapping(target = "withinServiceArea", ignore = true)
    @Mapping(source = "bio", target = "bio")
    @Mapping(source = "yearsOfExperience", target = "yearsOfExperience")
    SearchResponse toSearchResponse(Provider provider);

    List<ProviderResponse> toProviderResponseList(List<Provider> providers);

    @IterableMapping(qualifiedByName = "providerSummary")
    List<ProviderSummaryResponse> toProviderSummaryResponseList(List<Provider> providers);

    @Named("adminProviderResponse")
    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "email", target = "email")
    @Mapping(source = "phoneNumber", target = "phoneNumber")
    @Mapping(source = "profileImage", target = "profileImage")
    @Mapping(source = "enabled", target = "enabled")
    @Mapping(source = "verificationStatus", target = "verificationStatus")
    @Mapping(source = "rejectionReason", target = "rejectionReason")
    @Mapping(source = "serviceType.name", target = "serviceType")
    @Mapping(source = "serviceType.nameAr", target = "serviceTypeAr")
    @Mapping(source = "serviceType.category.name", target = "serviceCategory")
    @Mapping(source = "serviceType.category.nameAr", target = "serviceCategoryAr")
    @Mapping(source = "serviceType.id", target = "serviceTypeId")
    @Mapping(source = "completedJobs", target = "completedJobs")
    @Mapping(source = "totalBookings", target = "totalBookings")
    @Mapping(source = "averageRating", target = "averageRating")
    @Mapping(source = "acceptanceRate", target = "acceptanceRate")
    @Mapping(source = "cancellationRate", target = "cancellationRate")
    @Mapping(source = "location.area", target = "area")
    @Mapping(source = "createdAt", target = "createdAt")
    AdminProviderResponse toAdminProviderResponse(Provider provider);

    default List<CancellationResponse> mapCancellationHistory(List<com.aykhedma.model.booking.Booking> bookings) {
        if (bookings == null)
            return null;
        return bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.CANCELLED)
                .sorted((b1, b2) -> {
                    if (b1.getCancelledAt() == null && b2.getCancelledAt() == null)
                        return 0;
                    if (b1.getCancelledAt() == null)
                        return 1;
                    if (b2.getCancelledAt() == null)
                        return -1;
                    return b2.getCancelledAt().compareTo(b1.getCancelledAt());
                })
                .limit(10)
                .map(b -> CancellationResponse.builder()
                        .bookingId(b.getId())
                        .cancelledAt(b.getCancelledAt())
                        .cancellationReason(b.getCancellationReason())
                        .cancelledBy(b.getCancelledBy())
                        .otherPartyName(b.getConsumer().getName())
                        .build())
                .collect(Collectors.toList());
    }
}
