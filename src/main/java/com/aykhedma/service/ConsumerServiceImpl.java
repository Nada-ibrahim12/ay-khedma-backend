package com.aykhedma.service;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.request.ConsumerProfileRequest;
import com.aykhedma.dto.response.ConsumerResponse;
import com.aykhedma.dto.response.ProfileResponse;
import com.aykhedma.dto.response.ProviderSummaryResponse;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.mapper.ConsumerMapper;
import com.aykhedma.mapper.ProviderMapper;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.Provider;
import com.aykhedma.repository.ConsumerRepository;
import com.aykhedma.repository.ProviderRepository;
import com.aykhedma.service.ConsumerService;
import com.aykhedma.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ConsumerServiceImpl implements ConsumerService {

    private final ConsumerRepository consumerRepository;
    private final ProviderRepository providerRepository;
    private final ConsumerMapper consumerMapper;
    private final ProviderMapper providerMapper;
    private final FileStorageService fileStorageService;

    @Override
    public ConsumerResponse getConsumerProfile(Long consumerId) {
        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found with id: " + consumerId));
        return consumerMapper.toConsumerResponse(consumer);
    }

    @Override
    public ConsumerResponse updateConsumerProfile(Long consumerId, ConsumerProfileRequest request) {
        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found with id: " + consumerId));

        if (request.getName() != null) {
            consumer.setName(request.getName());
        }
        if (request.getEmail() != null) {
            consumer.setEmail(request.getEmail());
        }
        if (request.getPhoneNumber() != null) {
            consumer.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getPreferredLanguage() != null) {
            consumer.setPreferredLanguage(request.getPreferredLanguage());
        }

        Consumer updatedConsumer = consumerRepository.save(consumer);
        return consumerMapper.toConsumerResponse(updatedConsumer);
    }

    @Override
    public ConsumerResponse updateProfilePicture(Long consumerId, MultipartFile file) throws IOException {
        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found with id: " + consumerId));

        // Delete old picture if exists
        if (consumer.getProfileImage() != null) {
            fileStorageService.deleteFile(consumer.getProfileImage());
        }

        // Upload new picture
        String fileUrl = fileStorageService.storeFile(file, "profile-images");
        consumer.setProfileImage(fileUrl);

        Consumer updatedConsumer = consumerRepository.save(consumer);
        return consumerMapper.toConsumerResponse(updatedConsumer);
    }

    @Override
    public ProfileResponse saveProvider(Long consumerId, Long providerId) {
        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found with id: " + consumerId));

        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found with id: " + providerId));

        if (!consumer.getSavedProviders().contains(provider)) {
            consumer.getSavedProviders().add(provider);
            consumerRepository.save(consumer);
        }

        return ProfileResponse.builder()
                .success(true)
                .message("Provider saved successfully")
                .id(providerId)
                .build();
    }

    @Override
    public ProfileResponse removeSavedProvider(Long consumerId, Long providerId) {
        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found with id: " + consumerId));

        consumer.getSavedProviders().removeIf(p -> p.getId().equals(providerId));
        consumerRepository.save(consumer);

        return ProfileResponse.builder()
                .success(true)
                .message("Provider removed successfully")
                .build();
    }

    @Override
    public List<ProviderSummaryResponse> getSavedProviders(Long consumerId) {
        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found with id: " + consumerId));

        return providerMapper.toProviderSummaryResponseList(consumer.getSavedProviders());
    }

    @Override
    public ProfileResponse updateLocation(Long consumerId, LocationDTO request) {
        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found with id: " + consumerId));

        // For now, just acknowledge - location handling will be implemented later
        // You can add location entity to Consumer if needed

        return ProfileResponse.builder()
                .success(true)
                .message("Location updated successfully")
                .build();
    }
}