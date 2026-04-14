package com.aykhedma.service;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.request.ProviderProfileRequest;
import com.aykhedma.dto.request.WorkingDayRequest;
import com.aykhedma.dto.response.*;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.mapper.ProviderMapper;
import com.aykhedma.mapper.ScheduleMapper;
import com.aykhedma.model.booking.Booking;
import com.aykhedma.model.booking.BookingStatus;
import com.aykhedma.model.booking.Schedule;
import com.aykhedma.model.booking.TimeSlot;
import com.aykhedma.model.booking.TimeSlotStatus;
import com.aykhedma.model.booking.WorkingDay;
import com.aykhedma.model.document.Document;
import com.aykhedma.model.location.Location;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.user.VerificationStatus;
import com.aykhedma.repository.*;
import com.aykhedma.service.FileStorageService;
import com.aykhedma.service.ProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ProviderServiceImpl implements ProviderService {

    private static final int SLOT_STEP_MINUTES = 30;
    private static final long BUFFER_MINUTES = 30L;

    private final ProviderRepository providerRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final BookingRepository bookingRepository;
    private final WorkingDayRepository workingDayRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final DocumentRepository documentRepository;
    private final ProviderMapper providerMapper;
    private final ScheduleMapper scheduleMapper; // ADD THIS
    private final FileStorageService fileStorageService;
    private final LocationService locationService;

    @Override
    public ProviderResponse getProviderProfile(Long providerId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found with id: " + providerId));
        return providerMapper.toProviderResponse(provider);
    }

    @Override
    @CacheEvict(value = { "searchProvidersCache", "allProvidersCache" }, allEntries = true)
    public ProviderResponse updateProviderProfile(Long providerId, ProviderProfileRequest request) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found with id: " + providerId));

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
        if (request.getYearsOfExperience() != null) {
            provider.setYearsOfExperience(request.getYearsOfExperience());
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
                provider.setPriceType(
                        com.aykhedma.model.service.PriceType
                                .valueOf(request.getPriceType().trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid price type: " + request.getPriceType());
            }
        }

        if (request.getNationalId() != null) {
            if (providerRepository.existsByNationalIdAndIdNot(request.getNationalId(), providerId)) {
                throw new BadRequestException("National ID already exists");
            }
            provider.setNationalId(request.getNationalId());
        }

        if (request.getServiceAreaRadius() != null) {
            provider.setServiceAreaRadius(request.getServiceAreaRadius());
        }

        if (request.getEmergencyEnabled() != null) {
            provider.setEmergencyEnabled(request.getEmergencyEnabled());
        }

        // update location info
        if (request.getLocation() != null) {
            locationService.updateProviderLocation(providerId, request.getLocation());
        }

        Provider updatedProvider = providerRepository.save(provider);
        return providerMapper.toProviderResponse(updatedProvider);
    }

    @Override
    @CacheEvict(value = { "searchProvidersCache", "allProvidersCache" }, allEntries = true)
    public ProviderResponse updateProfilePicture(Long providerId, MultipartFile file) throws IOException {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        String oldProfileImage = provider.getProfileImage();
        String newFileUrl = null;

        try {
            newFileUrl = fileStorageService.storeFile(file, "profile-images");

            providerRepository.updateProfileImage(providerId, newFileUrl);

            if (oldProfileImage != null && !oldProfileImage.isEmpty()) {
                try {
                    fileStorageService.deleteFile(oldProfileImage);
                } catch (Exception e) {
                    // Ignore deletion error
                }
            }

            Provider updatedProvider = providerRepository.findById(providerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Ptovider not found with id: " + providerId));

            return providerMapper.toProviderResponse(updatedProvider);

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

    // @Override
    // public ProfileResponse updateProviderLocation(Long providerId, LocationDTO
    // request) {
    // Provider provider = providerRepository.findById(providerId)
    // .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));
    //
    // if (provider.getLocation() == null) {
    // provider.setLocation(new Location());
    // }
    //
    // provider.getLocation().setLatitude(request.getLatitude());
    // provider.getLocation().setLongitude(request.getLongitude());
    // provider.getLocation().setAddress(request.getAddress());
    // provider.getLocation().setArea(request.getArea());
    // provider.getLocation().setCity(request.getCity());
    //
    // providerRepository.save(provider);
    //
    // return ProfileResponse.builder()
    // .success(true)
    // .message("Location updated successfully")
    // .build();
    // }

    // @Override
    // public List<ProviderSummaryResponse> allProviders() {
    // List<Provider> providers = providerRepository.findAll();
    // return providerMapper.toProviderSummaryResponseList(providers);
    // }
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "allProvidersCache", key = "'allProviders'")
    public List<ProviderSummaryResponse> allProviders() {
        List<Provider> providers = providerRepository.findAll();
        return providers.stream()
                .map(providerMapper::toProviderSummaryResponse)
                .collect(Collectors.toList());
    }

    // @Override
    // @Transactional(readOnly = true)
    // public Page<SearchResponse> search(String keyword,
    // Long categoryId,
    // String categoryName,
    // Long consumerId,
    // Double radius,
    // String sortBy,
    // Pageable pageable) {

    // List<SearchResponse> fullList = searchList(keyword, categoryId, categoryName,
    // consumerId, radius, sortBy);

    // log.info("Returning {} results (maybe from cache)", fullList.size());

    // int start = (int) pageable.getOffset();
    // int end = Math.min((start + pageable.getPageSize()), fullList.size());

    // List<SearchResponse> pageContent =
    // start >= fullList.size() ? Collections.emptyList() : fullList.subList(start,
    // end);

    // return new PageImpl<>(pageContent, pageable, fullList.size());
    // }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "searchProvidersCache", key = "{#keyword,#categoryId,#categoryName,#consumerId,#radius,#sortBy,#pageable.pageNumber,#pageable.pageSize,#pageable.sort}")
    public Page<SearchResponse> search(String keyword,
            Long categoryId,
            String categoryName,
            Long consumerId,
            Double radius,
            String sortBy,
            Pageable pageable) {
        log.warn("CACHE MISS -> Fetching from DATABASE");

        Page<Provider> providersPage = providerRepository.searchProviders(keyword, categoryId, categoryName,
                Pageable.unpaged());

        if (consumerId == null || radius == null) {
            List<SearchResponse> responses = providersPage.getContent()
                    .stream()
                    .map(provider -> {
                        SearchResponse response = providerMapper.toSearchResponse(provider);
                        response.setDistance(null);
                        response.setEstimatedArrivalTime(null);
                        return response;
                    })
                    .collect(Collectors.toList());

            List<SearchResponse> sortedResponses = applySorting(responses, sortBy, null);

            return toPage(sortedResponses, pageable);
        }

        // === Location-based filtering ===
        try {
            // LocationDTO consumerLocation =
            // locationService.getConsumerLocation(consumerId);

            List<SearchResponse> filteredList = providersPage.getContent()
                    .stream()
                    .filter(provider -> provider.getLocation() != null)
                    .map(provider -> {
                        try {
                            double distance = locationService
                                    .calculateDistanceBetweenConsumerAndProvider(consumerId, provider.getId())
                                    .getDistanceKm();

                            if (radius != null && distance > radius) {
                                return null;
                            }

                            SearchResponse response = providerMapper.toSearchResponse(provider);
                            response.setDistance(distance);

                            // calc estimated arrival time (average speed 30 km/h in city)
                            int estimatedMinutes = (int) Math.round((distance / 30.0) * 60);
                            response.setEstimatedArrivalTime(estimatedMinutes);

                            // Check if within provider's service area
                            response.setWithinServiceArea(provider.getServiceAreaRadius() != null
                                    && distance <= provider.getServiceAreaRadius());

                            return response;
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            List<SearchResponse> sortedList = applySorting(filteredList, sortBy, consumerId);

            return toPage(sortedList, pageable);

        } catch (ResourceNotFoundException e) {

            // Return results without location filtering
            List<SearchResponse> responses = providersPage.getContent()
                    .stream()
                    .map(provider -> {
                        SearchResponse response = providerMapper.toSearchResponse(provider);
                        response.setDistance(null);
                        response.setEstimatedArrivalTime(null);
                        return response;
                    })
                    .collect(Collectors.toList());

            List<SearchResponse> sortedResponses = applySorting(responses, sortBy, null);
            return toPage(sortedResponses, pageable);
        }
    }

    private Page<SearchResponse> toPage(List<SearchResponse> responses, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return new PageImpl<>(responses);
        }

        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), responses.size());

        List<SearchResponse> pageContent = start >= responses.size()
                ? Collections.emptyList()
                : responses.subList(start, end);

        return new PageImpl<>(pageContent, pageable, responses.size());
    }

    private List<SearchResponse> applySorting(List<SearchResponse> responses, String sortBy, Long consumerId) {
        if (responses == null || responses.isEmpty()) {
            return new ArrayList<>();
        }

        String sortField = sortBy != null ? sortBy.toLowerCase() : "rating";
        switch (sortField) {
            case "price_low":
                return responses.stream()
                        .sorted(Comparator.comparing(SearchResponse::getPrice))
                        .collect(Collectors.toList());

            case "price_high":
                return responses.stream()
                        .sorted(Comparator.comparing(SearchResponse::getPrice).reversed())
                        .collect(Collectors.toList());

            case "experience":
                return responses.stream()
                        .sorted(Comparator.comparing(SearchResponse::getCompletedJobs).reversed())
                        .collect(Collectors.toList());

            case "distance":
                return responses.stream()
                        .filter(r -> r.getDistance() != null)
                        .sorted(Comparator.comparing(SearchResponse::getDistance))
                        .collect(Collectors.toList());

            case "rating":
            default:
                return responses.stream()
                        .sorted(Comparator.comparing(SearchResponse::getAverageRating,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                        .collect(Collectors.toList());
        }
    }
    @Override
    @Transactional(readOnly = true)
    public Page<SearchResponse> topRatedNearMe(Long consumerId, Double radius, Pageable pageable) {

        Page<Provider> providersPage =
                providerRepository.searchProviders(null, null, null, Pageable.unpaged());

        List<SearchResponse> ranked = providersPage.getContent().stream()
                .filter(p -> p.getLocation() != null)
                .map(provider -> {

                    try {
                        double distance = locationService
                                .calculateDistanceBetweenConsumerAndProvider(consumerId, provider.getId())
                                .getDistanceKm();

                        if (distance > radius) return null;

                        SearchResponse res = providerMapper.toSearchResponse(provider);

                        res.setDistance(distance);
                        res.setEstimatedArrivalTime((int) Math.round((distance / 30.0) * 60));


                        double score = calculateScore(provider, distance);
                        res.setScore(score);

                        return res;

                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(SearchResponse::getScore).reversed())
                .toList();

        return toPage(ranked, pageable);
    }
    private double calculateScore(Provider p, double distance) {

        double ratingWeight = 0.5;
        double distanceWeight = 0.3;
        double experienceWeight = 0.2;

        double ratingScore = (p.getAverageRating() != null ? p.getAverageRating() : 0) * 20;

        double distanceScore = Math.max(0, 100 - (distance * 10));


        double experienceScore = (p.getCompletedJobs() != null ? p.getCompletedJobs() : 0) / 10.0;

        return (ratingScore * ratingWeight)
                + (distanceScore * distanceWeight)
                + (experienceScore * experienceWeight);
    }

    @Override
    @Transactional
    public ScheduleResponse addWorkingDay(Long providerId, WorkingDayRequest request) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        if (request.getStartTime().isAfter(request.getEndTime()) ||
                request.getStartTime().equals(request.getEndTime())) {
            throw new BadRequestException("Start time must be before end time");
        }

        if (request.getDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("Cannot set working day for past dates");
        }

        Schedule schedule = provider.getSchedule();
        if (schedule == null) {
            schedule = Schedule.builder()
                    .workingDays(new ArrayList<>())
                    .timeSlots(new ArrayList<>())
                    .build();
            provider.setSchedule(schedule);
        }

        // working day already exists for this specific date?
        boolean exists = schedule.getWorkingDays().stream()
                .anyMatch(wd -> wd.getDate().equals(request.getDate()));

        if (exists) {
            throw new BadRequestException("Working day already configured for date: " + request.getDate());
        }

        WorkingDay workingDay = WorkingDay.builder()
                .date(request.getDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .schedule(schedule)
                .build();

        workingDay = workingDayRepository.save(workingDay);
        schedule.getWorkingDays().add(workingDay);

        generateTimeSlotsForSpecificDate(schedule, workingDay);

        providerRepository.save(provider);

        return scheduleMapper.toScheduleResponse(schedule);
    }

    /**
     * Generate time slots for a specific date only
     **/
    private void generateTimeSlotsForSpecificDate(Schedule schedule, WorkingDay workingDay) {
        LocalDate targetDate = workingDay.getDate();

        // check if slot already exists for this date
        boolean exists = schedule.getTimeSlots().stream()
                .anyMatch(slot -> slot.getDate().equals(targetDate));

        if (!exists) {
            TimeSlot timeSlot = TimeSlot.builder()
                    .date(targetDate)
                    .startTime(workingDay.getStartTime())
                    .endTime(workingDay.getEndTime())
                    .status(TimeSlotStatus.AVAILABLE)
                    .schedule(schedule)
                    .build();

            timeSlot = timeSlotRepository.save(timeSlot);
            schedule.getTimeSlots().add(timeSlot);
        }
    }

    @Override
    @Transactional
    public ScheduleResponse removeWorkingDay(Long providerId, Long workingDayId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        if (provider.getSchedule() == null) {
            throw new ResourceNotFoundException("Schedule not found");
        }

        WorkingDay workingDay = workingDayRepository.findById(workingDayId)
                .orElseThrow(() -> new ResourceNotFoundException("Working day not found"));

        LocalDate targetDate = workingDay.getDate();

        List<TimeSlot> slotsToRemove = provider.getSchedule().getTimeSlots().stream()
                .filter(slot -> slot.getDate().equals(targetDate))
                .filter(slot -> slot.getStatus() == TimeSlotStatus.AVAILABLE) // remove available slots only
                .collect(Collectors.toList());

        timeSlotRepository.deleteAll(slotsToRemove);
        provider.getSchedule().getTimeSlots().removeAll(slotsToRemove);

        workingDayRepository.delete(workingDay);
        provider.getSchedule().getWorkingDays().remove(workingDay);

        return scheduleMapper.toScheduleResponse(provider.getSchedule());
    }

    @Override
    @Transactional
    public ScheduleResponse updateWorkingDay(Long providerId, Long workingDayId, WorkingDayRequest request) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        if (provider.getSchedule() == null) {
            throw new ResourceNotFoundException("Schedule not found");
        }

        WorkingDay workingDay = workingDayRepository.findById(workingDayId)
                .orElseThrow(() -> new ResourceNotFoundException("Working day not found"));

        if (!workingDay.getSchedule().getId().equals(provider.getSchedule().getId())) {
            throw new BadRequestException("Working day does not belong to this provider");
        }

        // validate time range
        if (request.getStartTime().isAfter(request.getEndTime()) ||
                request.getStartTime().equals(request.getEndTime())) {
            throw new BadRequestException("Start time must be before end time");
        }

        // validate date
        if (request.getDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("Cannot set working day for past dates");
        }

        // Check if another working day exists for this date (excluding current)
        boolean exists = provider.getSchedule().getWorkingDays().stream()
                .filter(wd -> !wd.getId().equals(workingDayId))
                .anyMatch(wd -> wd.getDate().equals(request.getDate()));

        if (exists) {
            throw new BadRequestException("Another working day already configured for date: " + request.getDate());
        }

        // Update the working day
        workingDay.setDate(request.getDate());
        workingDay.setStartTime(request.getStartTime());
        workingDay.setEndTime(request.getEndTime());

        workingDay = workingDayRepository.save(workingDay);

        // Remove old time slot and create new one
        regenerateTimeSlotForSpecificDate(provider.getSchedule(), workingDay);

        providerRepository.save(provider);

        return scheduleMapper.toScheduleResponse(provider.getSchedule());
    }

    /**
     * Helper method to regenerate time slots for a working day after update
     */
    private void regenerateTimeSlotForSpecificDate(Schedule schedule, WorkingDay updatedWorkingDay) {
        LocalDate targetDate = updatedWorkingDay.getDate();

        // Remove existing available slot for this date
        List<TimeSlot> existingSlots = schedule.getTimeSlots().stream()
                .filter(slot -> slot.getDate().equals(targetDate))
                .filter(slot -> slot.getStatus() == TimeSlotStatus.AVAILABLE)
                .collect(Collectors.toList());

        if (!existingSlots.isEmpty()) {
            timeSlotRepository.deleteAll(existingSlots);
            schedule.getTimeSlots().removeAll(existingSlots);
        }

        // Create new slot
        TimeSlot timeSlot = TimeSlot.builder()
                .date(targetDate)
                .startTime(updatedWorkingDay.getStartTime())
                .endTime(updatedWorkingDay.getEndTime())
                .status(TimeSlotStatus.AVAILABLE)
                .schedule(schedule)
                .build();

        timeSlot = timeSlotRepository.save(timeSlot);
        schedule.getTimeSlots().add(timeSlot);
    }

    @Override
    @Transactional(readOnly = true)
    public ScheduleResponse getSchedule(Long providerId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        if (provider.getSchedule() == null) {
            return ScheduleResponse.builder()
                    .workingDays(new ArrayList<>())
                    .timeSlots(new ArrayList<>())
                    .build();
        }

        return scheduleMapper.toScheduleResponse(provider.getSchedule());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduleResponse.TimeSlotResponse> getAvailableTimeSlots(Long providerId, LocalDate date) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        if (provider.getSchedule() == null) {
            return List.of();
        }
        List<TimeSlot> slots = timeSlotRepository.findByScheduleIdAndDateAndStatus(
                provider.getSchedule().getId(), date, TimeSlotStatus.AVAILABLE);

        return toDiscreteStartTimeResponses(slots, date);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduleResponse.TimeSlotResponse> getTimeSlotsByDate(Long providerId, LocalDate date) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        if (provider.getSchedule() == null) {
            return List.of();
        }

        List<TimeSlot> slots = timeSlotRepository.findByScheduleIdAndDate(
                provider.getSchedule().getId(), date);

        return slots.stream()
                .map(this::mapToTimeSlotResponse)
                .sorted(Comparator.comparing(ScheduleResponse.TimeSlotResponse::getStartTime))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ScheduleResponse.TimeSlotResponse getTimeSlot(Long providerId, Long timeSlotId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found with id: " + providerId));

        if (provider.getSchedule() == null) {
            throw new ResourceNotFoundException("Provider has no schedule");
        }

        TimeSlot timeSlot = timeSlotRepository.findById(timeSlotId)
                .orElseThrow(() -> new ResourceNotFoundException("Time slot not found with id: " + timeSlotId));

        if (!timeSlot.getSchedule().getId().equals(provider.getSchedule().getId())) {
            throw new BadRequestException("Time slot does not belong to this provider");
        }

        return mapToTimeSlotResponse(timeSlot);
    }

    // @Override
    // @Transactional
    // public ScheduleResponse.TimeSlotResponse bookTimeSlot(Long providerId,
    // LocalDate date, LocalTime startTime,
    // Integer durationMinutes) {
    // Provider provider = providerRepository.findById(providerId)
    // .orElseThrow(() -> new ResourceNotFoundException("Provider not found with id:
    // " + providerId));

    // if (provider.getSchedule() == null) {
    // throw new ResourceNotFoundException("Provider has no schedule");
    // }

    // if (durationMinutes == null || durationMinutes <= 0) {
    // throw new BadRequestException("Duration must be positive");
    // }

    // validateHalfHourBoundary(startTime);

    // LocalTime bookingEndTime = startTime.plusMinutes(durationMinutes);

    // TimeSlot bookedSlot =
    // reserveTimeSlotWithBuffer(provider.getSchedule().getId(), date, startTime,
    // bookingEndTime);

    // return mapToTimeSlotResponse(bookedSlot);
    // }

    @Override
    @Transactional
    public ScheduleResponse.TimeSlotResponse cancelBooking(Long timeSlotId) {
        TimeSlot timeSlot = timeSlotRepository.findById(timeSlotId)
                .orElseThrow(() -> new ResourceNotFoundException("Time slot not found with id: " + timeSlotId));

        if (timeSlot.getStatus() != TimeSlotStatus.BOOKED) {
            throw new BadRequestException("Time slot is not booked");
        }

        LocalDate slotDate = timeSlot.getDate();
        LocalTime slotStartTime = timeSlot.getStartTime();
        Long scheduleId = timeSlot.getSchedule().getId();

        Booking booking = timeSlot.getBooking();
        if (booking != null) {
            restoreAvailabilityForCancelledBooking(booking);

            booking.setStatus(BookingStatus.CANCELLED);
            booking.setCancelledAt(LocalDateTime.now());
            booking.setCancelledBy("P");

            if (booking.getCancellationReason() == null || booking.getCancellationReason().isBlank()) {
                booking.setCancellationReason("Cancelled by provider");
            }

            bookingRepository.save(booking);
        } else {
            timeSlot.setStatus(TimeSlotStatus.AVAILABLE);
            timeSlotRepository.save(timeSlot);
            mergeContiguousAvailableSlots(scheduleId, slotDate);
        }

        return timeSlotRepository.findByScheduleIdAndDateAndStatus(scheduleId, slotDate, TimeSlotStatus.AVAILABLE)
                .stream()
                .filter(slot -> !slotStartTime.isBefore(slot.getStartTime())
                        && slotStartTime.isBefore(slot.getEndTime()))
                .findFirst()
                .map(this::mapToTimeSlotResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Available time slot not found after cancellation"));
    }

    @Override
    public TimeSlot reserveTimeSlotWithBuffer(Long scheduleId,
            LocalDate date,
            LocalTime bookingStart,
            LocalTime bookingEnd) {
        List<TimeSlot> availableSlots = timeSlotRepository
                .findByScheduleIdAndDateAndStatus(scheduleId, date, TimeSlotStatus.AVAILABLE)
                .stream()
                .sorted(Comparator.comparing(TimeSlot::getStartTime))
                .toList();

        TimeSlot containingSlot = availableSlots.stream()
                .filter(slot -> !bookingStart.isBefore(slot.getStartTime())
                        && !bookingEnd.isAfter(slot.getEndTime()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Selected start time with duration is not available"));

        LocalTime blockedStart = maxTime(containingSlot.getStartTime(), bookingStart.minusMinutes(BUFFER_MINUTES));
        LocalTime blockedEnd = minTime(containingSlot.getEndTime(), bookingEnd.plusMinutes(BUFFER_MINUTES));

        timeSlotRepository.delete(containingSlot);

        List<TimeSlot> slotsToSave = new ArrayList<>();

        if (containingSlot.getStartTime().isBefore(blockedStart)) {
            slotsToSave.add(TimeSlot.builder()
                    .date(date)
                    .startTime(containingSlot.getStartTime())
                    .endTime(blockedStart)
                    .status(TimeSlotStatus.AVAILABLE)
                    .schedule(containingSlot.getSchedule())
                    .build());
        }

        if (blockedStart.isBefore(bookingStart)) {
            slotsToSave.add(TimeSlot.builder()
                    .date(date)
                    .startTime(blockedStart)
                    .endTime(bookingStart)
                    .status(TimeSlotStatus.UNAVAILABLE)
                    .schedule(containingSlot.getSchedule())
                    .build());
        }

        TimeSlot bookedSlot = TimeSlot.builder()
                .date(date)
                .startTime(bookingStart)
                .endTime(bookingEnd)
                .status(TimeSlotStatus.BOOKED)
                .schedule(containingSlot.getSchedule())
                .build();
        slotsToSave.add(bookedSlot);

        if (bookingEnd.isBefore(blockedEnd)) {
            slotsToSave.add(TimeSlot.builder()
                    .date(date)
                    .startTime(bookingEnd)
                    .endTime(blockedEnd)
                    .status(TimeSlotStatus.UNAVAILABLE)
                    .schedule(containingSlot.getSchedule())
                    .build());
        }

        if (blockedEnd.isBefore(containingSlot.getEndTime())) {
            slotsToSave.add(TimeSlot.builder()
                    .date(date)
                    .startTime(blockedEnd)
                    .endTime(containingSlot.getEndTime())
                    .status(TimeSlotStatus.AVAILABLE)
                    .schedule(containingSlot.getSchedule())
                    .build());
        }

        List<TimeSlot> savedSlots = timeSlotRepository.saveAll(slotsToSave);

        return savedSlots.stream()
                .filter(slot -> slot.getStatus() == TimeSlotStatus.BOOKED)
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Failed to reserve booked time slot"));
    }

    @Override
    public void restoreAvailabilityForCancelledBooking(Booking booking) {
        if (booking.getTimeSlot() == null) {
            return;
        }

        TimeSlot bookedSlot = timeSlotRepository.findById(booking.getTimeSlot().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Booked time slot not found"));

        List<TimeSlot> daySlots = timeSlotRepository.findByScheduleIdAndDate(
                bookedSlot.getSchedule().getId(),
                bookedSlot.getDate());

        LocalTime bookingStart = bookedSlot.getStartTime();
        LocalTime bookingEnd = bookedSlot.getEndTime();

        daySlots.stream()
                .filter(slot -> slot.getStatus() == TimeSlotStatus.UNAVAILABLE)
                .filter(slot -> slot.getEndTime().equals(bookingStart) || slot.getStartTime().equals(bookingEnd))
                .forEach(slot -> slot.setStatus(TimeSlotStatus.AVAILABLE));

        bookedSlot.setStatus(TimeSlotStatus.AVAILABLE);
        booking.setTimeSlot(null);

        timeSlotRepository.saveAll(daySlots);
        mergeContiguousAvailableSlots(bookedSlot.getSchedule().getId(), bookedSlot.getDate());
    }

    @Override
    public void mergeContiguousAvailableSlots(Long scheduleId, LocalDate date) {
        List<TimeSlot> allDaySlots = timeSlotRepository.findByScheduleIdAndDate(scheduleId, date)
                .stream()
                .sorted(Comparator.comparing(TimeSlot::getStartTime))
                .toList();

        List<TimeSlot> availableSlots = allDaySlots.stream()
                .filter(slot -> slot.getStatus() == TimeSlotStatus.AVAILABLE)
                .sorted(Comparator.comparing(TimeSlot::getStartTime))
                .toList();

        if (availableSlots.size() < 2) {
            return;
        }

        List<TimeSlot> slotsToDelete = new ArrayList<>();
        TimeSlot current = availableSlots.get(0);

        for (int i = 1; i < availableSlots.size(); i++) {
            TimeSlot next = availableSlots.get(i);

            if (!next.getStartTime().isAfter(current.getEndTime())) {
                if (next.getEndTime().isAfter(current.getEndTime())) {
                    current.setEndTime(next.getEndTime());
                }
                slotsToDelete.add(next);
            } else {
                current = next;
            }
        }

        if (!slotsToDelete.isEmpty()) {
            timeSlotRepository.saveAll(availableSlots);
            timeSlotRepository.deleteAll(slotsToDelete);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ScheduleResponse.TimeSlotResponse> getUpcomingAvailableSlots(Long providerId, Integer days) {

        if (days == null || days <= 0) {
            days = 7; // Default to 7 days if invalid
        }
        if (days > 60) {
            days = 60;
        }

        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found with id: " + providerId));

        if (provider.getSchedule() == null) {
            return new ArrayList<>();
        }

        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusDays(days);

        List<TimeSlot> availableSlots = timeSlotRepository.findByScheduleIdAndDateBetweenAndStatus(
                provider.getSchedule().getId(),
                today,
                endDate,
                TimeSlotStatus.AVAILABLE);

        return toDiscreteStartTimeResponses(availableSlots, null).stream()
                .sorted(Comparator
                        .comparing(ScheduleResponse.TimeSlotResponse::getDate)
                        .thenComparing(ScheduleResponse.TimeSlotResponse::getStartTime))
                .collect(Collectors.toList());
    }

    // /**
    // * Helper method to generate time slots for a working day
    // */
    // private void generateTimeSlotsForWorkingDay(Schedule schedule, WorkingDay
    // workingDay) {
    // LocalDate startDate = LocalDate.now();
    // LocalDate endDate = startDate.plusDays(30); // Generate slots for next 30
    // days
    //
    // List<TimeSlot> newSlots = new ArrayList<>();
    //
    // for (LocalDate date = startDate; !date.isAfter(endDate); date =
    // date.plusDays(1)) {
    // if (date == workingDay.getDate()) {
    // // Check if slot already exists for this date
    // LocalDate finalDate = date;
    // boolean exists = schedule.getTimeSlots().stream()
    // .anyMatch(slot -> slot.getDate().equals(finalDate));
    //
    // if (!exists) {
    // TimeSlot timeSlot = TimeSlot.builder()
    // .date(date)
    // .startTime(workingDay.getStartTime())
    // .endTime(workingDay.getEndTime())
    // .status(TimeSlotStatus.AVAILABLE)
    // .schedule(schedule)
    // .build();
    // newSlots.add(timeSlot);
    // }
    // }
    // }
    //
    // if (!newSlots.isEmpty()) {
    // timeSlotRepository.saveAll(newSlots);
    // schedule.getTimeSlots().addAll(newSlots);
    // }
    // }

    private List<ScheduleResponse.TimeSlotResponse> toDiscreteStartTimeResponses(List<TimeSlot> availableSlots,
            LocalDate fallbackDate) {
        if (availableSlots == null || availableSlots.isEmpty()) {
            return List.of();
        }

        Map<String, ScheduleResponse.TimeSlotResponse> uniqueTimes = new LinkedHashMap<>();

        for (TimeSlot slot : availableSlots) {
            LocalDate date = slot.getDate() != null ? slot.getDate() : fallbackDate;
            if (date == null) {
                continue;
            }

            LocalTime cursor = roundUpToHalfHour(slot.getStartTime());
            while (cursor.isBefore(slot.getEndTime())) {
                String key = date + "_" + cursor;

                uniqueTimes.putIfAbsent(key, ScheduleResponse.TimeSlotResponse.builder()
                        .id(null)
                        .date(date.toString())
                        .startTime(cursor)
                        .endTime(cursor.plusMinutes(SLOT_STEP_MINUTES))
                        .isBooked(false)
                        .status(TimeSlotStatus.AVAILABLE.name())
                        .build());

                cursor = cursor.plusMinutes(SLOT_STEP_MINUTES);
            }
        }

        return uniqueTimes.values().stream()
                .sorted(Comparator
                        .comparing(ScheduleResponse.TimeSlotResponse::getDate)
                        .thenComparing(ScheduleResponse.TimeSlotResponse::getStartTime))
                .collect(Collectors.toList());
    }

    private LocalTime roundUpToHalfHour(LocalTime time) {
        int minute = time.getMinute();
        int remainder = minute % SLOT_STEP_MINUTES;
        if (remainder == 0 && time.getSecond() == 0 && time.getNano() == 0) {
            return time;
        }

        LocalTime rounded = time.plusMinutes(SLOT_STEP_MINUTES - remainder);
        return rounded.withSecond(0).withNano(0);
    }

    @Override
    public void validateHalfHourBoundary(LocalTime time) {
        if (time == null) {
            throw new BadRequestException("Start time is required");
        }

        if (time.getSecond() != 0 || time.getNano() != 0) {
            throw new BadRequestException("Start time must be in HH:mm format");
        }

        int minute = time.getMinute();
        if (minute != 0 && minute != 30) {
            throw new BadRequestException("Start time must be on a 30-minute boundary");
        }
    }

    @Override
    public LocalTime maxTime(LocalTime a, LocalTime b) {
        return a.isAfter(b) ? a : b;
    }

    @Override
    public LocalTime minTime(LocalTime a, LocalTime b) {
        return a.isBefore(b) ? a : b;
    }

    /**
     * Helper method to map TimeSlot to TimeSlotResponse
     */
    private ScheduleResponse.TimeSlotResponse mapToTimeSlotResponse(TimeSlot timeSlot) {
        return ScheduleResponse.TimeSlotResponse.builder()
                .id(timeSlot.getId())
                .date(timeSlot.getDate().toString())
                .startTime(timeSlot.getStartTime())
                .endTime(timeSlot.getEndTime())
                .isBooked(timeSlot.getStatus() == TimeSlotStatus.BOOKED)
                .status(timeSlot.getStatus().name())
                .build();
    }

    @Override
    public DocumentResponse uploadDocument(Long providerId, MultipartFile file, String documentType)
            throws IOException {
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

    // @Override
    // public ProviderResponse updateEmergencyStatus(Long providerId, boolean
    // enabled) {
    // Provider provider = providerRepository.findById(providerId)
    // .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));
    //
    // provider.setEmergencyEnabled(enabled);
    // Provider updatedProvider = providerRepository.save(provider);
    //
    // return providerMapper.toProviderResponse(updatedProvider);
    // }

    @Override
    public VerificationStatus getVerificationStatus(Long providerId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));
        VerificationStatus status = provider.getVerificationStatus();
        return status;
    }

}