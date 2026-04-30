package com.aykhedma.mapper;

import com.aykhedma.dto.response.ProviderResponseResponse;
import com.aykhedma.model.emergency.ProviderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
    uses = {ProviderMapper.class, EmergencyRequestMapper.class})
public interface ProviderResponseMapper
{
    @Mapping(source = "id", target = "id")
    @Mapping(source = "provider", target = "provider", qualifiedByName = "providerSummary")
    @Mapping(source = "emergencyRequest", target = "emergencyRequest")
    @Mapping(source = "estimatedArrivalTime", target = "estimatedArrivalTime")
    @Mapping(source = "distance", target = "distance")
    @Mapping(source = "proposedPrice", target = "proposedPrice")
    @Mapping(source = "notes", target = "notes")
    @Mapping(source = "responseTime", target = "responseTime")
    ProviderResponseResponse toProviderResponseResponse(ProviderResponse providerResponse);
}
