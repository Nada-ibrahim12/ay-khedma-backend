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
    @Transactional(rollbackFor = Exception.class)
    public ConsumerResponse updateProfilePicture(Long consumerId, MultipartFile file) throws IOException {
        // Find consumer but don't load the saved providers collection
        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found with id: " + consumerId));

        String oldProfileImage = consumer.getProfileImage();
        String newFileUrl = null;

        try {
            newFileUrl = fileStorageService.storeFile(file, "profile-images");

            consumerRepository.updateProfileImage(consumerId, newFileUrl);

            if (oldProfileImage != null && !oldProfileImage.isEmpty()) {
                try {
                    fileStorageService.deleteFile(oldProfileImage);
                } catch (Exception e) {
                    // Ignore deletion error
                }
            }

            Consumer updatedConsumer = consumerRepository.findById(consumerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Consumer not found with id: " + consumerId));

            return consumerMapper.toConsumerResponse(updatedConsumer);

        } catch (Exception e) {
            // If upload succeeded but update failed, clean up the uploaded file
            if (newFileUrl != null) {
                try {
                    fileStorageService.deleteFile(newFileUrl);
                } catch (Exception cleanupEx) {
                }
            }
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProfileResponse deleteProfilePicture(Long consumerId) throws IOException {
        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found with id: " + consumerId));

        String oldProfileImage = consumer.getProfileImage();

        if (oldProfileImage == null || oldProfileImage.isEmpty()) {
            return ProfileResponse.builder()
                    .success(true)
                    .message("No profile picture to delete")
                    .build();
        }

        try {
            fileStorageService.deleteFile(oldProfileImage);

            consumer.setProfileImage(null);
            consumerRepository.save(consumer);

            return ProfileResponse.builder()
                    .success(true)
                    .message("Profile picture deleted successfully")
                    .build();

        } catch (Exception e) {
            throw new IOException("Failed to delete profile picture: " + e.getMessage(), e);
        }
    }

    @Override
    public ProfileResponse saveProvider(Long consumerId, Long providerId) {
        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found with id: " + consumerId));

        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found with id: " + providerId));

        boolean alreadySaved = consumerRepository.isProviderSavedNative(consumerId, providerId);

        if (!alreadySaved) {
            consumerRepository.insertSavedProvider(consumerId, providerId);
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

        int deletedCount = consumerRepository.deleteSavedProvider(consumerId, providerId);

        if (deletedCount > 0) {
            consumer.getSavedProviders().removeIf(p -> p.getId().equals(providerId));
        }

        return ProfileResponse.builder()
                .success(deletedCount > 0)
                .message(deletedCount > 0 ? "Provider removed successfully" : "Provider was not in saved list")
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