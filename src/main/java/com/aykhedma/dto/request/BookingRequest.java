package com.aykhedma.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingRequest {

    @NotNull(message = "Provider ID is required")
    @Positive(message = "Provider ID must be positive")
    private Long providerId;

    @NotNull(message = "Service type ID is required")
    @Positive(message = "Service type ID must be positive")
    private Long serviceTypeId;

    @NotNull(message = "Requested date is required")
    @FutureOrPresent(message = "Requested date must be today or in the future")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate requestedDate;

    @NotNull(message = "Requested time is required")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime requestedTime;

    @Size(max = 500, message = "Problem description cannot exceed 500 characters")
    private String problemDescription;

    @DecimalMin(value = "0.0", inclusive = false, message = "Proposed price must be greater than 0")
    @DecimalMax(value = "100000.0", message = "Proposed price cannot exceed 100,000")
    private Double proposedPrice;
}