package com.aykhedma.mapper;

import com.aykhedma.dto.response.ProviderResponse;
import com.aykhedma.dto.response.ProviderSummaryResponse;
import com.aykhedma.model.user.Provider;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = {ScheduleMapper.class})
public interface ProviderMapper {

    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "email", target = "email")
    @Mapping(source = "phoneNumber", target = "phoneNumber")
    @Mapping(source = "profileImage", target = "profileImage")
    @Mapping(source = "bio", target = "bio")
    @Mapping(source = "verificationStatus", target = "verificationStatus")
    @Mapping(source = "completedJobs", target = "completedJobs")
    @Mapping(source = "serviceType.name", target = "serviceType")
    @Mapping(source = "serviceType.id", target = "serviceTypeId")
    @Mapping(source = "location", target = "location")
    @Mapping(source = "emergencyEnabled", target = "emergencyEnabled")
    @Mapping(source = "averageRating", target = "averageRating")
    @Mapping(source = "price", target = "price")
    @Mapping(source = "priceType", target = "priceType")
    @Mapping(source = "averageTime", target = "averageTime")
    @Mapping(source = "acceptanceRate", target = "acceptanceRate")
    @Mapping(source = "schedule", target = "schedule")
    @Mapping(target = "documents", ignore = true)
    ProviderResponse toProviderResponse(Provider provider);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "profileImage", target = "profileImage")
    @Mapping(source = "serviceType.name", target = "serviceType")
    @Mapping(source = "averageRating", target = "averageRating")
    @Mapping(source = "completedJobs", target = "completedJobs")
    @Mapping(source = "price", target = "price")
    @Mapping(source = "priceType", target = "priceType")
    @Mapping(source = "emergencyEnabled", target = "emergencyEnabled")
    @Mapping(target = "distance", ignore = true)
    @Mapping(target = "estimatedArrivalTime", ignore = true)
    ProviderSummaryResponse toProviderSummaryResponse(Provider provider);

    List<ProviderResponse> toProviderResponseList(List<Provider> providers);

    List<ProviderSummaryResponse> toProviderSummaryResponseList(List<Provider> providers);
}