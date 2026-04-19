package com.aykhedma.service;

import com.aykhedma.dto.request.InteractionRatingRequest;
import com.aykhedma.dto.response.InteractionRatingResponse;

import java.util.List;

public interface InteractionRatingService {
    InteractionRatingResponse submitRating(Long consumerId, InteractionRatingRequest request);
    List<InteractionRatingResponse> getProviderRatings(Long providerId);
}
