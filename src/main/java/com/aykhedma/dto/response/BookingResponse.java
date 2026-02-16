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
public class BookingResponse {

    private Long id;
    private ConsumerSummaryResponse consumer;
    private ProviderSummaryResponse provider;
    private String serviceType;
    private LocalDate requestedDate;
    private LocalTime requestedStartTime;
    private LocalTime requestedEndTime;
    private String problemDescription;
    private Double initialPrice;
    private Double finalPrice;
    private BookingStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime completedAt;
    private Double consumerRating;
    private String consumerReview;
}