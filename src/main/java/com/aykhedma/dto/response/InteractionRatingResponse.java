package com.aykhedma.dto.response;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractionRatingResponse {
    private Long id;
    private Long consumerId;
    private String consumerName;
    private Double rating;
    private String comment;
    private LocalDateTime createdAt;
}
