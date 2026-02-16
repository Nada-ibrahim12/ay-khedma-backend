package com.aykhedma.dto.request;

import com.aykhedma.dto.location.LocationDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmergencyRequestDTO {

    @NotNull(message = "Service type ID is required")
    @Positive(message = "Service type ID must be positive")
    private Long serviceTypeId;

    @NotNull(message = "Location is required")
    @Valid
    private LocationDTO location;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;

    @Min(value = 1, message = "Search radius must be at least 1 km")
    @Max(value = 50, message = "Search radius cannot exceed 50 km")
    private Integer searchRadius = 10;
}