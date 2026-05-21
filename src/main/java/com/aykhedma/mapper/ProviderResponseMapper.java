package com.aykhedma.mapper;

import com.aykhedma.dto.response.EmergencyRequestResponse;
import com.aykhedma.dto.response.ProviderResponseResponse;
import com.aykhedma.model.emergency.EmergencyRequest;
import com.aykhedma.model.emergency.ProviderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, uses = { ProviderMapper.class })
public interface ProviderResponseMapper {
    @Mapping(source = "id", target = "id")
    @Mapping(source = "provider", target = "provider", qualifiedByName = "providerSummary")
    @Mapping(source = "emergencyRequest", target = "emergencyRequest", qualifiedByName = "noProviderResponsesEmergencyRequest")
    @Mapping(source = "estimatedArrivalTime", target = "estimatedArrivalTime")
    @Mapping(source = "distance", target = "distance")
    @Mapping(source = "proposedPrice", target = "proposedPrice")
    @Mapping(source = "notes", target = "notes")
    @Mapping(source = "responseTime", target = "responseTime")
    ProviderResponseResponse toProviderResponseResponse(ProviderResponse providerResponse);

    @Named("noProviderResponsesEmergencyRequest")
    @Mapping(source = "id", target = "id")
    @Mapping(source = "consumer", target = "consumer")
    @Mapping(source = "serviceType.name", target = "serviceType")
    @Mapping(source = "serviceType.nameAr", target = "serviceTypeAr")
    @Mapping(source = "location", target = "location")
    @Mapping(source = "price", target = "price")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "selectedProvider", target = "selectedProvider", qualifiedByName = "providerSummary")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(target = "providerResponses", ignore = true)
    EmergencyRequestResponse toEmergencyRequestResponse(EmergencyRequest emergencyRequest);
}
