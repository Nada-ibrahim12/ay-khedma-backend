package com.aykhedma.mapper;

import com.aykhedma.dto.request.ConsumerProfileRequest;
import com.aykhedma.dto.request.ProviderProfileRequest;
import com.aykhedma.dto.response.ConsumerResponse;
import com.aykhedma.dto.response.ProviderResponse;
import com.aykhedma.dto.response.ProviderSummaryResponse;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.user.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    // Add this method to map ServiceType to String
    @Named("mapServiceTypeToString")
    default String mapServiceTypeToString(ServiceType serviceType) {
        return serviceType != null ? serviceType.getName() : null;
    }

    // Consumer mappings
    @Mapping(target = "profileImage", ignore = true)
    @Mapping(target = "credentialsNonExpired", ignore = true)
    @Mapping(target = "location", ignore = true)
    Consumer toEntity(ConsumerProfileRequest request);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "email", target = "email")
    @Mapping(source = "phoneNumber", target = "phoneNumber")
    @Mapping(source = "profileImage", target = "profileImage")
    @Mapping(source = "preferredLanguage", target = "preferredLanguage")
    @Mapping(source = "role", target = "role")
    @Mapping(source = "averageRating", target = "averageRating")
    @Mapping(source = "totalBookings", target = "totalBookings")
    @Mapping(source = "location", target = "location")
    ConsumerResponse toResponse(Consumer consumer);

    // Provider mappings
    @Mapping(target = "profileImage", ignore = true)
    @Mapping(target = "credentialsNonExpired", ignore = true)
    @Mapping(target = "averageTime", ignore = true)
    @Mapping(target = "serviceType", ignore = true)
    @Mapping(target = "serviceArea", ignore = true)
    @Mapping(target = "responseTime", ignore = true)
    Provider toEntity(ProviderProfileRequest request);

    // ProviderResponse mapping - FIXED: Added qualifiedByName
    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "email", target = "email")
    @Mapping(source = "phoneNumber", target = "phoneNumber")
    @Mapping(source = "profileImage", target = "profileImage")
    @Mapping(source = "bio", target = "bio")
    @Mapping(source = "verificationStatus", target = "verificationStatus")
    @Mapping(source = "completedJobs", target = "completedJobs")
    @Mapping(source = "serviceType", target = "serviceType", qualifiedByName = "mapServiceTypeToString")  // FIXED
    @Mapping(source = "serviceType.id", target = "serviceTypeId")
    @Mapping(source = "location", target = "location")
    @Mapping(source = "emergencyEnabled", target = "emergencyEnabled")
    @Mapping(source = "averageRating", target = "averageRating")
    @Mapping(source = "price", target = "price")
    @Mapping(source = "priceType", target = "priceType")
    @Mapping(source = "averageTime", target = "averageTime")
    @Mapping(source = "acceptanceRate", target = "acceptanceRate")
    @Mapping(source = "schedule", target = "schedule")
    ProviderResponse toResponse(Provider provider);

    // ProviderSummaryResponse mapping - ADD THIS if you need it
    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "profileImage", target = "profileImage")
    @Mapping(source = "serviceType", target = "serviceType", qualifiedByName = "mapServiceTypeToString")
    @Mapping(source = "averageRating", target = "averageRating")
    @Mapping(source = "completedJobs", target = "completedJobs")
    @Mapping(source = "price", target = "price")
    @Mapping(source = "priceType", target = "priceType")
    @Mapping(source = "emergencyEnabled", target = "emergencyEnabled")
    ProviderSummaryResponse toSummaryResponse(Provider provider);
}