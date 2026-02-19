package com.aykhedma.mapper;

import com.aykhedma.dto.response.ConsumerResponse;
import com.aykhedma.dto.response.ProviderSummaryResponse;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.Provider;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

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
    @Mapping(target = "savedProviders", expression = "java(mapSavedProviders(consumer.getSavedProviders()))")
    ConsumerResponse toConsumerResponse(Consumer consumer);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "profileImage", target = "profileImage")
    @Mapping(source = "serviceType.name", target = "serviceType")
    @Mapping(source = "averageRating", target = "averageRating")
    @Mapping(source = "completedJobs", target = "completedJobs")
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
}