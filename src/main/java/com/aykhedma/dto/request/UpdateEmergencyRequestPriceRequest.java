package com.aykhedma.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
public class UpdateEmergencyRequestPriceRequest
{
    @NotNull(message = "Emergency request ID is required")
    @Positive(message = "Emergency request ID must be positive")
    private Long emergencyRequestId;

    @NotNull(message = "Updated price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Updated price must be greater than 0")
    @DecimalMax(value = "100000.0", message = "Updated price cannot exceed 100,000")
    private double updatedPrice;
}
