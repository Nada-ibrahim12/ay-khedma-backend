package com.aykhedma.service;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.request.ProviderProfileRequest;
import com.aykhedma.dto.request.WorkingDayRequest;
import com.aykhedma.dto.response.*;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.mapper.ProviderMapper;
import com.aykhedma.mapper.ScheduleMapper;
import com.aykhedma.model.booking.Schedule;
import com.aykhedma.model.booking.TimeSlot;
import com.aykhedma.model.booking.WorkingDay;
import com.aykhedma.model.document.Document;
import com.aykhedma.model.location.Location;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.user.Provider;
import com.aykhedma.repository.*;
import com.aykhedma.service.FileStorageService;
import com.aykhedma.service.ProviderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ProviderServiceImpl implements ProviderService {

    private final ProviderRepository providerRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final WorkingDayRepository workingDayRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final DocumentRepository documentRepository;
    private final ProviderMapper providerMapper;
    private final ScheduleMapper scheduleMapper; // ADD THIS
    private final FileStorageService fileStorageService;

    @Override
    public ProviderResponse getProviderProfile(Long providerId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found with id: " + providerId));
        return providerMapper.toProviderResponse(provider);
    }

    @Override
    public ProviderResponse updateProviderProfile(Long providerId, ProviderProfileRequest request) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found with id: " + providerId));

        // Update basic info
        if (request.getName() != null) {
            provider.setName(request.getName());
        }
        if (request.getEmail() != null) {
            provider.setEmail(request.getEmail());
        }
        if (request.getPhoneNumber() != null) {
            provider.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getBio() != null) {
            provider.setBio(request.getBio());
        }

        // Update service info
        if (request.getServiceTypeId() != null) {
            ServiceType serviceType = serviceTypeRepository.findById(request.getServiceTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Service type not found"));
            provider.setServiceType(serviceType);
        }

        if (request.getPrice() != null) {
            provider.setPrice(request.getPrice());
        }

        if (request.getPriceType() != null) {
            try {
                provider.setPriceType(com.aykhedma.model.service.PriceType.valueOf(request.getPriceType()));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid price type: " + request.getPriceType());
            }
        }

        if (request.getServiceArea() != null) {
            provider.setServiceArea(request.getServiceArea());
        }

        if (request.getEmergencyEnabled() != null) {
            provider.setEmergencyEnabled(request.getEmergencyEnabled());
        }

        Provider updatedProvider = providerRepository.save(provider);
        return providerMapper.toProviderResponse(updatedProvider);
    }

    @Override
    public ProviderResponse updateProfilePicture(Long providerId, MultipartFile file) throws IOException {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        if (provider.getProfileImage() != null) {
            fileStorageService.deleteFile(provider.getProfileImage());
        }

        String fileUrl = fileStorageService.storeFile(file, "profile-images");
        provider.setProfileImage(fileUrl);

        Provider updatedProvider = providerRepository.save(provider);
        return providerMapper.toProviderResponse(updatedProvider);
    }

    @Override
    public ProfileResponse updateProviderLocation(Long providerId, LocationDTO request) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        if (provider.getLocation() == null) {
            provider.setLocation(new Location());
        }

        provider.getLocation().setLatitude(request.getLatitude());
        provider.getLocation().setLongitude(request.getLongitude());
        provider.getLocation().setAddress(request.getAddress());
        provider.getLocation().setArea(request.getArea());
        provider.getLocation().setCity(request.getCity());

        providerRepository.save(provider);

        return ProfileResponse.builder()
                .success(true)
                .message("Location updated successfully")
                .build();
    }

    @Override
    public List<ProviderSummaryResponse> findNearbyProviders(Double latitude, Double longitude, Long serviceTypeId, Double radius) {
        return List.of();
    }

    @Override
    public List<ProviderSummaryResponse> searchProviders(String keyword, Long categoryId, Double minRating) {
        return List.of();
    }

    @Override
    public ScheduleResponse addWorkingDay(Long providerId, WorkingDayRequest request) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        if (request.getStartTime().isAfter(request.getEndTime()) ||
                request.getStartTime().equals(request.getEndTime())) {
            throw new BadRequestException("Start time must be before end time");
        }

        Schedule schedule = provider.getSchedule();
        if (schedule == null) {
            schedule = new Schedule();
            provider.setSchedule(schedule);
        }

        boolean exists = schedule.getWorkingDays().stream()
                .anyMatch(wd -> wd.getDayOfWeek() == request.getDayOfWeek());

        if (exists) {
            throw new BadRequestException("Working day already configured for " + request.getDayOfWeek());
        }

        WorkingDay workingDay = WorkingDay.builder()
                .dayOfWeek(request.getDayOfWeek())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .schedule(schedule)
                .build();

        workingDay = workingDayRepository.save(workingDay);
        schedule.getWorkingDays().add(workingDay);

        providerRepository.save(provider);

        // USE THE MAPPER INSTEAD OF MANUAL MAPPING
        return scheduleMapper.toScheduleResponse(schedule);
    }

    @Override
    public ScheduleResponse removeWorkingDay(Long providerId, Long workingDayId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        if (provider.getSchedule() == null) {
            throw new ResourceNotFoundException("Schedule not found");
        }

        workingDayRepository.deleteById(workingDayId);

        // USE THE MAPPER INSTEAD OF MANUAL MAPPING
        return scheduleMapper.toScheduleResponse(provider.getSchedule());
    }

    @Override
    public ScheduleResponse getSchedule(Long providerId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        if (provider.getSchedule() == null) {
            return new ScheduleResponse();
        }

        // USE THE MAPPER INSTEAD OF MANUAL MAPPING
        return scheduleMapper.toScheduleResponse(provider.getSchedule());
    }

    @Override
    public List<LocalTime> getAvailableTimeSlots(Long providerId, LocalDate date) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        if (provider.getSchedule() == null) {
            return List.of();
        }

        List<TimeSlot> slots = timeSlotRepository.findByScheduleIdAndDateAndIsBookedFalse(
                provider.getSchedule().getId(), date);

        return slots.stream()
                .map(TimeSlot::getStartTime)
                .sorted()
                .collect(Collectors.toList());
    }

    @Override
    public DocumentResponse uploadDocument(Long providerId, MultipartFile file, String documentType) throws IOException {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        String fileUrl = fileStorageService.storeFile(file, "documents");

        Document document = Document.builder()
                .title(file.getOriginalFilename())
                .type(documentType)
                .filePath(fileUrl)
                .provider(provider)
                .build();

        document = documentRepository.save(document);

        return DocumentResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .type(document.getType())
                .filePath(document.getFilePath())
                .uploadedDate(document.getUploadedDate())
                .build();
    }

    @Override
    public List<DocumentResponse> getProviderDocuments(Long providerId) {
        List<Document> documents = documentRepository.findByProviderId(providerId);

        return documents.stream()
                .map(doc -> DocumentResponse.builder()
                        .id(doc.getId())
                        .title(doc.getTitle())
                        .type(doc.getType())
                        .filePath(doc.getFilePath())
                        .uploadedDate(doc.getUploadedDate())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public ProfileResponse deleteDocument(Long providerId, Long documentId) {
        documentRepository.deleteByProviderIdAndId(providerId, documentId);

        return ProfileResponse.builder()
                .success(true)
                .message("Document deleted successfully")
                .build();
    }

//    @Override
//    public ProviderResponse updateEmergencyStatus(Long providerId, boolean enabled) {
//        Provider provider = providerRepository.findById(providerId)
//                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));
//
//        provider.setEmergencyEnabled(enabled);
//        Provider updatedProvider = providerRepository.save(provider);
//
//        return providerMapper.toProviderResponse(updatedProvider);
//    }

    @Override
    public ProviderResponse getVerificationStatus(Long providerId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        return providerMapper.toProviderResponse(provider);
    }

}