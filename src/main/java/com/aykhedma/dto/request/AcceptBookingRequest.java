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
public class AcceptBookingRequest
{
    @NotNull(message = "Booking ID is required")
    @Positive(message = "Booking ID must be positive")
    private Long bookingId;

    @NotNull(message = "Estimated duration is required")
    @Min(value = 30, message = "Estimated duration cannot be less than 30 minutes")
    private Long estimatedDuration; // In minutes

    private boolean overrideWorkingHours = false; // Set true when provider wants to accept after knowing it will end after the working hours
}
