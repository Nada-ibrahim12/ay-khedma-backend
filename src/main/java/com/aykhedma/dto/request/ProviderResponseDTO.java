package com.aykhedma.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderResponseDTO {

    @NotNull(message = "Emergency request ID is required")
    @Positive(message = "Emergency request ID must be positive")
    private Long emergencyRequestId;

    @NotNull(message = "Proposed price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Proposed price must be greater than 0")
    @DecimalMax(value = "100000.0", message = "Proposed price cannot exceed 100,000")
    private Double proposedPrice;

    @Size(max = 200, message = "Notes cannot exceed 200 characters")
    private String notes;
}