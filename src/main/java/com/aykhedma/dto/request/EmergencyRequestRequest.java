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
public class EmergencyRequestRequest
{
    @NotNull(message = "Service type ID is required")
    @Positive(message = "Service type ID must be positive")
    private Long serviceTypeId;

    @NotNull(message = "Location is required")
    @Valid
    private LocationDTO location;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    @DecimalMax(value = "100000.0", message = "Price cannot exceed 100,000")
    private double price;

    @NotNull(message = "Description is required")
    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;
}
