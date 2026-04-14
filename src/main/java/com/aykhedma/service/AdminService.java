package com.aykhedma.service;

import com.aykhedma.dto.request.UpdateUserRequest;
import com.aykhedma.dto.response.ProviderResponse;
import com.aykhedma.dto.response.UserResponse;
import com.aykhedma.model.user.UserType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface AdminService {
    List<ProviderResponse> getPendingProviders();
    ProviderResponse getProviderDetails(Long providerId);
    ProviderResponse approveProvider(Long providerId);
    ProviderResponse rejectProvider(Long providerId, String reason);

    // User Management
    Page<UserResponse> searchUsers(
            UserType role,
            Boolean status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable);
    
    UserResponse updateUser(Long userId, UpdateUserRequest request);
    UserResponse suspendUser(Long userId);
    UserResponse reactivateUser(Long userId);
    void deleteUser(Long userId);
}
