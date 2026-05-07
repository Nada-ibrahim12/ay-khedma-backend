package com.aykhedma.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerReviewResponse {
    private Long id;
    private Long providerId;
    private String providerName;
    private Double rating;
    private String review;
    private LocalDateTime completedAt;
}
