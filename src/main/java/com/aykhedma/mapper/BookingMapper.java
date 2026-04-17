package com.aykhedma.mapper;

import com.aykhedma.dto.response.BookingResponse;
import com.aykhedma.model.booking.Booking;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE,
    uses = {ConsumerMapper.class, ProviderMapper.class})
public interface BookingMapper
{
    @Mapping(source = "id", target = "id")
    @Mapping(source = "consumer", target = "consumer")
    @Mapping(source = "provider", target = "provider", qualifiedByName = "providerSummary")
    @Mapping(source = "requestedDate", target = "requestedDate")
    @Mapping(source = "requestedStartTime", target = "requestedStartTime")
    @Mapping(source = "estimatedDuration", target = "estimatedDuration")
    @Mapping(source = "problemDescription", target = "problemDescription")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "createdAt", target = "createdAt")
    @Mapping(source = "acceptedAt", target = "acceptedAt")
    @Mapping(source = "declinedAt", target = "declinedAt")
    @Mapping(source = "expiredAt", target = "expiredAt")
    @Mapping(source = "completedAt", target = "completedAt")
    @Mapping(source = "cancelledAt", target = "cancelledAt")
    @Mapping(source = "cancellationReason", target = "cancellationReason")
    @Mapping(source = "cancelledBy", target = "cancelledBy")
    @Mapping(source = "consumerRating", target = "providerRating")
    @Mapping(source = "consumerReview", target = "providerReview")
    @Mapping(source = "providerRating", target = "consumerRating")
    @Mapping(source = "providerReview", target = "consumerReview")
    @Mapping(source = "punctualityRating", target = "punctualityRating")
    @Mapping(source = "commitmentRating", target = "commitmentRating")
    @Mapping(source = "qualityOfWorkRating", target = "qualityOfWorkRating")
    BookingResponse toBookingResponse(Booking booking);
}
