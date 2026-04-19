package com.aykhedma.service;

import com.aykhedma.dto.request.InteractionRatingRequest;
import com.aykhedma.dto.response.InteractionRatingResponse;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.mapper.InteractionRatingMapper;
import com.aykhedma.model.rating.InteractionRating;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.Provider;
import com.aykhedma.repository.ConsumerRepository;
import com.aykhedma.repository.InteractionRatingRepository;
import com.aykhedma.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InteractionRatingServiceImpl implements InteractionRatingService {

    private final InteractionRatingRepository interactionRatingRepository;
    private final ProviderRepository providerRepository;
    private final ConsumerRepository consumerRepository;
    private final InteractionRatingMapper interactionRatingMapper;

    @Override
    @Transactional
    public InteractionRatingResponse submitRating(Long consumerId, InteractionRatingRequest request) {
        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found"));

        Provider provider = providerRepository.findById(request.getProviderId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        InteractionRating rating = InteractionRating.builder()
                .consumer(consumer)
                .provider(provider)
                .rating(request.getRating().doubleValue())
                .comment(request.getComment())
                .build();

        InteractionRating savedRating = interactionRatingRepository.save(rating);

        // Update provider interaction metrics
        updateProviderInteractionMetrics(provider, request.getRating().doubleValue());

        return interactionRatingMapper.toResponse(savedRating);
    }

    @Override
    public List<InteractionRatingResponse> getProviderRatings(Long providerId) {
        if (!providerRepository.existsById(providerId)) {
            throw new ResourceNotFoundException("Provider not found");
        }
        List<InteractionRating> ratings = interactionRatingRepository.findByProviderId(providerId);
        return interactionRatingMapper.toResponseList(ratings);
    }

    private void updateProviderInteractionMetrics(Provider provider, double newRating) {
        int oldCount = provider.getInteractionRatingCount() != null ? provider.getInteractionRatingCount() : 0;
        double oldAvg = provider.getAverageInteractionRating() != null ? provider.getAverageInteractionRating() : 0.0;

        int newCount = oldCount + 1;
        double newAvg = ((oldAvg * oldCount) + newRating) / newCount;

        provider.setInteractionRatingCount(newCount);
        provider.setAverageInteractionRating(Math.round(newAvg * 10.0) / 10.0);
        providerRepository.save(provider);
    }
}
