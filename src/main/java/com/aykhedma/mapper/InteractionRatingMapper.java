package com.aykhedma.mapper;

import com.aykhedma.dto.response.InteractionRatingResponse;
import com.aykhedma.model.rating.InteractionRating;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface InteractionRatingMapper {

    @Mapping(source = "consumer.id", target = "consumerId")
    @Mapping(source = "consumer.name", target = "consumerName")
    InteractionRatingResponse toResponse(InteractionRating rating);

    List<InteractionRatingResponse> toResponseList(List<InteractionRating> ratings);
}
