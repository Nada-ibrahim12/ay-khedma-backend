package com.aykhedma.mapper;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.response.LocationResponse;
import com.aykhedma.model.location.Location;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LocationMapper {

    // Entity to DTO mapping - LocationDTO doesn't have id and country fields
    @Mapping(source = "latitude", target = "latitude")
    @Mapping(source = "longitude", target = "longitude")
    @Mapping(source = "address", target = "address")
    @Mapping(source = "area", target = "area")
    @Mapping(source = "city", target = "city")
    // Note: id and country are not in LocationDTO, so remove them
    LocationDTO toDto(Location location);

    // DTO to Entity mapping (for creating new entities)
    @Mapping(target = "id", ignore = true)  // id is auto-generated
    @Mapping(target = "coordinates", ignore = true)
    @Mapping(target = "country", constant = "Egypt") // Set default country
    Location toEntity(LocationDTO locationDTO);

    // Update existing entity from DTO
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "coordinates", ignore = true)
    @Mapping(target = "country", ignore = true) // Don't update country
    void updateEntity(LocationDTO dto, @MappingTarget Location location);

    // Entity to Response mapping - LocationResponse has all fields
    @Mapping(source = "id", target = "id")
    @Mapping(source = "latitude", target = "latitude")
    @Mapping(source = "longitude", target = "longitude")
    @Mapping(source = "address", target = "address")
    @Mapping(source = "area", target = "area")
    @Mapping(source = "city", target = "city")
    @Mapping(source = "country", target = "country")
    @Mapping(expression = "java(location.getFormattedAddress())", target = "formattedAddress")
    @Mapping(target = "success", constant = "true")
    @Mapping(target = "message", constant = "Location processed successfully")
    LocationResponse toResponse(Location location);

    // Custom response with message
    default LocationResponse toResponseWithMessage(Location location, String message, boolean success) {
        LocationResponse response = toResponse(location);
        response.setMessage(message);
        response.setSuccess(success);
        return response;
    }

    // List mappings
    List<LocationDTO> toDtoList(List<Location> locations);
    List<LocationResponse> toResponseList(List<Location> locations);
}