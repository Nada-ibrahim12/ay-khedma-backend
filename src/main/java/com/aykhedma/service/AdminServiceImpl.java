package com.aykhedma.service;

import com.aykhedma.dto.response.ProviderResponse;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.mapper.ProviderMapper;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.user.VerificationStatus;
import com.aykhedma.repository.ProviderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final ProviderRepository providerRepository;
    private final ProviderMapper providerMapper;
    private final ProviderService providerService;
    private final NotificationService notificationService;

    @Override
    public List<ProviderResponse> getPendingProviders() {
        List<Provider> pendingProviders = providerRepository.findByVerificationStatus(VerificationStatus.PENDING);
        return providerMapper.toProviderResponseList(pendingProviders);
    }

    @Override
    public ProviderResponse getProviderDetails(Long providerId) {
        ProviderResponse response = providerService.getProviderProfile(providerId);
        response.setDocuments(providerService.getProviderDocuments(providerId));
        return response;
    }

    @Override
    @Transactional
    public ProviderResponse approveProvider(Long providerId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found with id: " + providerId));
        
        provider.setVerificationStatus(VerificationStatus.VERIFIED);
        provider.setRejectionReason(null);
        
        Provider savedProvider = providerRepository.save(provider);
        notificationService.sendProviderApprovalEmail(savedProvider.getEmail());
        
        return providerMapper.toProviderResponse(savedProvider);
    }

    @Override
    @Transactional
    public ProviderResponse rejectProvider(Long providerId, String reason) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found with id: " + providerId));
        
        provider.setVerificationStatus(VerificationStatus.REJECTED);
        provider.setRejectionReason(reason);
        
        Provider savedProvider = providerRepository.save(provider);
        notificationService.sendProviderRejectionEmail(savedProvider.getEmail(), reason);
        
        return providerMapper.toProviderResponse(savedProvider);
    }
}
