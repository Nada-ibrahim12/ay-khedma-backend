package com.aykhedma.service;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.request.ConsumerProfileRequest;
import com.aykhedma.dto.response.ConsumerResponse;
import com.aykhedma.dto.response.ProfileResponse;
import com.aykhedma.dto.response.ProviderSummaryResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface ConsumerService {
    ConsumerResponse getConsumerProfile(Long consumerId);
    ConsumerResponse updateConsumerProfile(Long consumerId, ConsumerProfileRequest request);
    ConsumerResponse updateProfilePicture(Long consumerId, MultipartFile file) throws IOException;
    ProfileResponse saveProvider(Long consumerId, Long providerId);
    ProfileResponse removeSavedProvider(Long consumerId, Long providerId);
    List<ProviderSummaryResponse> getSavedProviders(Long consumerId);
    ProfileResponse updateLocation(Long consumerId, LocationDTO request);
}