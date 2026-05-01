package com.aykhedma.mapper;

import com.aykhedma.dto.response.EmergencyRequestResponse;
import com.aykhedma.model.emergency.EmergencyRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
    uses = {ConsumerMapper.class, LocationMapper.class, ProviderMapper.class})
public interface EmergencyRequestMapper
{
    @Mapping(source = "id", target = "id")
    @Mapping(source = "consumer", target = "consumer")
    @Mapping(source = "serviceType.name", target = "serviceType")
    @Mapping(source = "location", target = "location")
    @Mapping(source = "price", target = "price")
    @Mapping(source = "description", target = "description")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "selectedProvider", target = "selectedProvider")
    @Mapping(source = "createdAt", target = "createdAt")
    EmergencyRequestResponse toEmergencyRequestResponse(EmergencyRequest emergencyRequest);
}
