package com.aykhedma.mapper;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.request.ConsumerProfileRequest;
import com.aykhedma.dto.response.CancellationResponse;
import com.aykhedma.dto.response.ConsumerResponse;
import com.aykhedma.dto.response.ProviderSummaryResponse;
import com.aykhedma.model.booking.BookingStatus;
import com.aykhedma.model.location.Location;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.Provider;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ConsumerMapper {

    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "email", target = "email")
    @Mapping(source = "phoneNumber", target = "phoneNumber")
    @Mapping(source = "profileImage", target = "profileImage")
    @Mapping(source = "preferredLanguage", target = "preferredLanguage")
    @Mapping(source = "averageRating", target = "averageRating")
    @Mapping(source = "totalBookings", target = "totalBookings")
    @Mapping(source = "cancelledBookings", target = "cancelledBookings")
    @Mapping(target = "cancellationRate", expression = "java(consumer.getCancellationRate())")
    @Mapping(target = "cancellationHistory", expression = "java(mapCancellationHistory(consumer.getBookings()))")
    @Mapping(target = "savedProviders", expression = "java(mapSavedProviders(consumer.getSavedProviders()))")
    @Mapping(source = "location", target = "location", qualifiedByName = "mapLocationToDTO")
    ConsumerResponse toConsumerResponse(Consumer consumer);

    // DTO to Entity mappings (for updates)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "savedProviders", ignore = true)
    @Mapping(target = "bookings", ignore = true)
    @Mapping(target = "location", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "credentialsNonExpired", ignore = true)
    @Mapping(target = "enabled", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "profileImage", ignore = true)
    @Mapping(target = "averageRating", ignore = true)
    @Mapping(target = "totalBookings", ignore = true)
    @Mapping(target = "cancelledBookings", ignore = true)
    Consumer toEntity(ConsumerProfileRequest request);

    @Named("mapLocationToDTO")
    default LocationDTO mapLocationToDTO(Location location) {
        if (location == null) {
            return null;
        }

        return LocationDTO.builder()
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .address(location.getAddress())
                .area(location.getArea())
                .city(location.getCity())
                .build();
    }

    @Named("mapDTOToLocation")
    default Location mapDTOToLocation(LocationDTO locationDTO) {
        if (locationDTO == null) {
            return null;
        }

        return Location.builder()
                .latitude(locationDTO.getLatitude())
                .longitude(locationDTO.getLongitude())
                .address(locationDTO.getAddress())
                .area(locationDTO.getArea())
                .city(locationDTO.getCity())
                .build();
    }

    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "profileImage", target = "profileImage")
    @Mapping(source = "serviceType.name", target = "serviceType")
    @Mapping(source = "averageRating", target = "averageRating")
    @Mapping(source = "completedJobs", target = "completedJobs")
    @Mapping(source = "totalBookings", target = "totalBookings")
    @Mapping(source = "cancelledBookings", target = "cancelledBookings")
    @Mapping(source = "price", target = "price")
    @Mapping(source = "priceType", target = "priceType")
    ProviderSummaryResponse toProviderSummary(Provider provider);

    default List<ProviderSummaryResponse> mapSavedProviders(List<Provider> providers) {
        if (providers == null) return null;
        return providers.stream()
                .map(this::toProviderSummary)
                .toList();
    }

    List<ConsumerResponse> toConsumerResponseList(List<Consumer> consumers);

    default List<CancellationResponse> mapCancellationHistory(List<com.aykhedma.model.booking.Booking> bookings) {
        if (bookings == null) return null;
        return bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.CANCELLED)
                .sorted((b1, b2) -> {
                    if (b1.getCancelledAt() == null && b2.getCancelledAt() == null) return 0;
                    if (b1.getCancelledAt() == null) return 1;
                    if (b2.getCancelledAt() == null) return -1;
                    return b2.getCancelledAt().compareTo(b1.getCancelledAt());
                })
                .limit(10)
                .map(b -> CancellationResponse.builder()
                        .bookingId(b.getId())
                        .cancelledAt(b.getCancelledAt())
                        .cancellationReason(b.getCancellationReason())
                        .cancelledBy(b.getCancelledBy())
                        .otherPartyName(b.getProvider().getName())
                        .build())
                .collect(Collectors.toList());
    }
}
