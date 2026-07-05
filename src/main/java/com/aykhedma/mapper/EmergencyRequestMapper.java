package com.aykhedma.mapper;

import com.aykhedma.dto.response.EmergencyRequestResponse;
import com.aykhedma.dto.response.ProviderResponseResponse;
import com.aykhedma.model.emergency.EmergencyRequest;
import com.aykhedma.model.emergency.ProviderResponse;
import com.aykhedma.model.emergency.ProviderResponseType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, uses = { ConsumerMapper.class,
        LocationMapper.class, ProviderMapper.class })
public interface EmergencyRequestMapper {
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
    @Mapping(source = "providerResponses", target = "providerResponses", qualifiedByName = "noEmergencyRequestAcceptedProviderResponses")
    @Mapping(target = "consumerReview", expression = "java(emergencyRequest.getConsumerReview() != null && !emergencyRequest.getConsumerReview().trim().isEmpty() ? emergencyRequest.getConsumerReview() : null)")
    @Mapping(target = "providerReview", expression = "java(emergencyRequest.getProviderReview() != null && !emergencyRequest.getProviderReview().trim().isEmpty() ? emergencyRequest.getProviderReview() : null)")
    EmergencyRequestResponse toEmergencyRequestResponse(EmergencyRequest emergencyRequest);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "provider", target = "provider", qualifiedByName = "providerSummary")
    @Mapping(target = "emergencyRequest", ignore = true)
    @Mapping(source = "estimatedArrivalTime", target = "estimatedArrivalTime")
    @Mapping(source = "distance", target = "distance")
    @Mapping(source = "proposedPrice", target = "proposedPrice")
    @Mapping(source = "notes", target = "notes")
    @Mapping(source = "responseTime", target = "responseTime")
    ProviderResponseResponse toProviderResponseResponse(ProviderResponse providerResponse);

    @Named("noEmergencyRequestAcceptedProviderResponses")
    default List<ProviderResponseResponse> mapProviderResponses(List<ProviderResponse> providerResponses) {
        if (providerResponses == null)
            return null;

        return providerResponses.stream()
                .filter(pr -> pr.getResponseType() == ProviderResponseType.ACCEPTED_REQUEST)
                .map(this::toProviderResponseResponse)
                .toList();
    }
}
