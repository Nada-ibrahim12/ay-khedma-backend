package com.aykhedma.service;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.request.ProviderProfileRequest;
import com.aykhedma.dto.request.WorkingDayRequest;
import com.aykhedma.dto.response.*;
import com.aykhedma.model.booking.TimeSlot;
import com.aykhedma.model.user.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public interface ProviderService {
    // Profile Management
    ProviderResponse getProviderProfile(Long providerId);
    ProviderResponse updateProviderProfile(Long providerId, ProviderProfileRequest request);
    ProviderResponse updateProfilePicture(Long providerId, MultipartFile file) throws IOException;

    // Location & Search
//    ProfileResponse updateProviderLocation(Long providerId, LocationDTO request);
//    List<ProviderSummaryResponse> findNearbyProviders(Double latitude, Double longitude, Long serviceTypeId, Double radius);
    List<ProviderSummaryResponse> allProviders();
    Page<SearchResponse> search(
            String keyword,
            Long categoryId,
            String categoryName,
            Long consumerId,
            Double radius,
            String sortBy,
            Pageable pageable);

    // Schedule Management
    ScheduleResponse addWorkingDay(Long providerId, WorkingDayRequest request);
    ScheduleResponse removeWorkingDay(Long providerId, Long workingDayId);
    ScheduleResponse updateWorkingDay(Long providerId, Long workingDayId, WorkingDayRequest request);
    ScheduleResponse getSchedule(Long providerId);
    List<ScheduleResponse.TimeSlotResponse> getAvailableTimeSlots(Long providerId, LocalDate date);
    List<ScheduleResponse.TimeSlotResponse> getTimeSlotsByDate(Long providerId, LocalDate date);
    ScheduleResponse.TimeSlotResponse getTimeSlot(Long providerId, Long timeSlotId);
    ScheduleResponse.TimeSlotResponse bookTimeSlot(Long timeSlotId, Integer durationMinutes);
    List<ScheduleResponse.TimeSlotResponse> getUpcomingAvailableSlots(Long providerId, Integer days);

    // Documents
    DocumentResponse uploadDocument(Long providerId, MultipartFile file, String documentType) throws IOException;
    List<DocumentResponse> getProviderDocuments(Long providerId);
    ProfileResponse deleteDocument(Long providerId, Long documentId);

    // Status & Verification
//    ProviderResponse updateEmergencyStatus(Long providerId, boolean enabled);
    VerificationStatus getVerificationStatus(Long providerId);
}