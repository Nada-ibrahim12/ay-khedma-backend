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
public class BookingRequest
{
    @NotNull(message = "Provider ID is required")
    @Positive(message = "Provider ID must be positive")
    private Long providerId;

    @NotNull(message = "Requested date is required")
    @FutureOrPresent(message = "Requested date must be today or in the future")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate requestedDate;

    @NotNull(message = "Requested time is required")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime requestedTime;

    @NotNull(message = "Problem description is required")
    @Size(max = 1000, message = "Problem description cannot exceed 1000 characters")
    private String problemDescription;
}
