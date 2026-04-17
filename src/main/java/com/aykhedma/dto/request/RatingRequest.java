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
public class RatingRequest {

    @NotNull(message = "Booking ID is required")
    @Positive(message = "Booking ID must be positive")
    private Long bookingId;

    @NotNull(message = "Punctuality rating is required")
    @Min(value = 1, message = "Punctuality rating must be at least 1")
    @Max(value = 5, message = "Punctuality rating cannot exceed 5")
    private Integer punctualityRating;

    @NotNull(message = "Commitment rating is required")
    @Min(value = 1, message = "Commitment rating must be at least 1")
    @Max(value = 5, message = "Commitment rating cannot exceed 5")
    private Integer commitmentRating;

    @NotNull(message = "Quality of work rating is required")
    @Min(value = 1, message = "Quality of work rating must be at least 1")
    @Max(value = 5, message = "Quality of work rating cannot exceed 5")
    private Integer qualityOfWorkRating;

    @Size(max = 500, message = "Review cannot exceed 500 characters")
    private String review;
}