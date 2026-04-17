package com.aykhedma.dto.response;

import com.aykhedma.model.booking.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse
{
    private Long id;
    private ConsumerSummaryResponse consumer;
    private ProviderSummaryResponse provider;
    private LocalDate requestedDate;
    private LocalTime requestedStartTime;
    private Long estimatedDuration; // In minutes
    private String problemDescription;
    private BookingStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime declinedAt;
    private LocalDateTime expiredAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private String cancellationReason;
    private String cancelledBy;
    
    private Double punctualityRating;
    private Double commitmentRating;
    private Double qualityOfWorkRating;
    private Double providerRating;
    private String providerReview;
    private Double consumerRating;
    private String consumerReview;
}
