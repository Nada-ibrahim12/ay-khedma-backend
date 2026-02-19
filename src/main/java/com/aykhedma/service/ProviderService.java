package com.aykhedma.service;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.request.ProviderProfileRequest;
import com.aykhedma.dto.request.WorkingDayRequest;
import com.aykhedma.dto.response.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface ProviderService {
    // Profile Management
    ProviderResponse getProviderProfile(Long providerId);
    ProviderResponse updateProviderProfile(Long providerId, ProviderProfileRequest request);
    ProviderResponse updateProfilePicture(Long providerId, MultipartFile file) throws IOException;

    // Location & Search
    ProfileResponse updateProviderLocation(Long providerId, LocationDTO request);
    List<ProviderSummaryResponse> findNearbyProviders(Double latitude, Double longitude, Long serviceTypeId, Double radius);
    List<ProviderSummaryResponse> searchProviders(String keyword, Long categoryId, Double minRating);

    // Schedule Management
    ScheduleResponse addWorkingDay(Long providerId, WorkingDayRequest request);
    ScheduleResponse removeWorkingDay(Long providerId, Long workingDayId);
    ScheduleResponse getSchedule(Long providerId);
    List<LocalTime> getAvailableTimeSlots(Long providerId, LocalDate date);

    // Documents
    DocumentResponse uploadDocument(Long providerId, MultipartFile file, String documentType) throws IOException;
    List<DocumentResponse> getProviderDocuments(Long providerId);
    ProfileResponse deleteDocument(Long providerId, Long documentId);

    // Status & Verification
//    ProviderResponse updateEmergencyStatus(Long providerId, boolean enabled);
    ProviderResponse getVerificationStatus(Long providerId);
}