package com.aykhedma.dto.request;

import com.aykhedma.dto.location.LocationDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceRecommendationRequest
{
    @NotNull(message = "Service type ID is required")
    @Positive(message = "Service type ID must be positive")
    private Long serviceTypeId;

    @NotNull(message = "Location is required")
    @Valid
    private LocationDTO location;
}
