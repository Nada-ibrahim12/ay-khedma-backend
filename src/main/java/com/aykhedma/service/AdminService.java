package com.aykhedma.service;

import com.aykhedma.dto.response.ProviderResponse;

import java.util.List;

public interface AdminService {
    List<ProviderResponse> getPendingProviders();
    ProviderResponse getProviderDetails(Long providerId);
    ProviderResponse approveProvider(Long providerId);
    ProviderResponse rejectProvider(Long providerId, String reason);
}
