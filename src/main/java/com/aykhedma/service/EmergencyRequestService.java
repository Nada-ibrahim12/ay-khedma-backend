package com.aykhedma.service;

import com.aykhedma.dto.request.EmergencyRatingRequest;
import com.aykhedma.dto.request.ProviderEmergencyRatingRequest;
import com.aykhedma.dto.request.EmergencyRequestRequest;
import com.aykhedma.dto.request.ProviderResponseRequest;
import com.aykhedma.dto.request.UpdateEmergencyRequestPriceRequest;
import com.aykhedma.dto.response.EmergencyRequestResponse;
import com.aykhedma.dto.response.ProviderResponseResponse;

import java.util.List;

public interface EmergencyRequestService
{
    EmergencyRequestResponse getCurrentEmergencyRequest (Long consumerId);

    EmergencyRequestResponse requestEmergencyRequest (Long consumerId, EmergencyRequestRequest request);

    void broadcastEmergencyRequest (Long requestId);

    List<ProviderResponseResponse> getPendingEmergencyRequests (Long providerId);

    ProviderResponseResponse acceptEmergencyRequest (Long providerId, ProviderResponseRequest request);

    ProviderResponseResponse declineEmergencyRequest (Long providerId, Long providerResponseId);

    ProviderResponseResponse acceptProviderResponse (Long consumerId, Long providerResponseId);

    ProviderResponseResponse declineProviderResponse (Long consumerId, Long providerResponseId);

    EmergencyRequestResponse updateEmergencyRequestPrice (Long consumerId, UpdateEmergencyRequestPriceRequest request);

    EmergencyRequestResponse completeEmergencyRequest (Long consumerId, Long emergencyRequestId);

    EmergencyRequestResponse cancelEmergencyRequest (Long consumerId, Long emergencyRequestId);

    List<EmergencyRequestResponse> getAcceptedEmergencyRequests (Long userId);

    List<EmergencyRequestResponse> getEmergencyRequestsHistory (Long userId);

    EmergencyRequestResponse submitEmergencyRequestRating(Long consumerId, EmergencyRatingRequest ratingRequest);

    EmergencyRequestResponse submitConsumerEmergencyRequestRating(Long providerId, ProviderEmergencyRatingRequest ratingRequest);
}
