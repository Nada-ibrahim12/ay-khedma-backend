package com.aykhedma.service;

import com.aykhedma.dto.request.UpdateUserRequest;
import com.aykhedma.dto.response.ProviderResponse;
import com.aykhedma.dto.response.UserResponse;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.mapper.ProviderMapper;
import com.aykhedma.mapper.UserMapper;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.user.User;
import com.aykhedma.model.user.UserType;
import com.aykhedma.model.user.VerificationStatus;
import com.aykhedma.repository.ProviderRepository;
import com.aykhedma.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final ProviderRepository providerRepository;
    private final ProviderMapper providerMapper;
    private final ProviderService providerService;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

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

    @Override
    public Page<UserResponse> searchUsers(
            UserType role, Boolean status,
            LocalDateTime startDate, LocalDateTime endDate,
            Pageable pageable) {
        
        Page<User> users = userRepository.searchUsers(role, status, startDate, endDate, pageable);
        
        return users.map(userMapper::toUserResponse);
    }

    @Override
    public Page<ProviderResponse> searchProviders(
            String keyword, VerificationStatus status, 
            Boolean enabled, Pageable pageable) {
        
        Page<Provider> providers = providerRepository.findAllProvidersForAdmin(keyword, status, enabled, pageable);
        
        return providers.map(providerMapper::toProviderResponse);
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
                
        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName());
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            user.setEmail(request.getEmail());
        }
        if (request.getEnabled() != null) {
            user.setEnabled(request.getEnabled());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        
        userRepository.save(user);
        return userMapper.toUserResponse(user);
    }

    @Override
    @Transactional
    public UserResponse suspendUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        user.setEnabled(false);
        
        if (user instanceof Provider) {
            notificationService.sendProviderBlockedEmail(user.getEmail());
        }
        
        return userMapper.toUserResponse(user);
    }

    @Override
    @Transactional
    public UserResponse reactivateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        user.setEnabled(true);
        return userMapper.toUserResponse(user);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        userRepository.delete(user);
    }
}
