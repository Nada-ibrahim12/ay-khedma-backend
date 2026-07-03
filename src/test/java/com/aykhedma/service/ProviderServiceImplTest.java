package com.aykhedma.service;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.request.ProviderProfileRequest;
import com.aykhedma.dto.request.WorkingDayRequest;
import com.aykhedma.dto.response.ProviderDistanceProjection;
import com.aykhedma.dto.response.ProviderResponse;
import com.aykhedma.dto.response.ScheduleResponse;
import com.aykhedma.dto.response.SearchResponse;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.mapper.ProviderMapper;
import com.aykhedma.mapper.ScheduleMapper;
import com.aykhedma.model.booking.Schedule;
import com.aykhedma.model.booking.TimeSlot;
import com.aykhedma.model.booking.TimeSlotStatus;
import com.aykhedma.model.booking.WorkingDay;
import com.aykhedma.model.service.PriceType;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.user.Provider;
import com.aykhedma.repository.DocumentRepository;
import com.aykhedma.repository.ProviderRepository;
import com.aykhedma.repository.ServiceTypeRepository;
import com.aykhedma.repository.TimeSlotRepository;
import com.aykhedma.repository.WorkingDayRepository;
import com.aykhedma.dto.response.DocumentResponse;
import com.aykhedma.dto.response.ProfileResponse;
import com.aykhedma.model.user.VerificationStatus;
import com.aykhedma.dto.response.WeeklyScheduleResponse;
import com.aykhedma.model.document.Document;
import com.aykhedma.model.user.VerificationStatus;
import com.aykhedma.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import com.aykhedma.exception.BadRequestException;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Provider Service Unit Tests")
class ProviderServiceImplTest {

        @Mock
        private ProviderRepository providerRepository;

        @Mock
        private ServiceTypeRepository serviceTypeRepository;

        @Mock
        private WorkingDayRepository workingDayRepository;

        @Mock
        private TimeSlotRepository timeSlotRepository;

        @Mock
        private DocumentRepository documentRepository;

        @Mock
        private ProviderMapper providerMapper;

        @Mock
        private ScheduleMapper scheduleMapper;

        @Mock
        private FileStorageService fileStorageService;

        @Mock
        private LocationService locationService;

        @InjectMocks
        private ProviderServiceImpl providerService;

        @Mock
        private SearchCacheService searchCacheService;

        private Provider provider;
        private ProviderResponse providerResponse;

        private final Long PROVIDER_ID = 1L;
        private final Long NON_EXISTENT_ID = 999L;

        @BeforeEach
        void setUp() {
                provider = TestDataFactory.createProvider(PROVIDER_ID);
                providerResponse = ProviderResponse.builder()
                                .id(PROVIDER_ID)
                                .name(provider.getName())
                                .email(provider.getEmail())
                                .build();
        }

        @Nested
        @DisplayName("Get Provider Profile Tests")
        class GetProviderProfileTests {

                @Test
                @DisplayName("Should return provider profile when provider exists")
                void getProviderProfile_ExistingId_ReturnsProviderResponse() {
                        when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
                        when(providerMapper.toProviderResponse(provider)).thenReturn(providerResponse);

                        ProviderResponse result = providerService.getProviderProfile(PROVIDER_ID);

                        assertThat(result).isNotNull();
                        assertThat(result.getId()).isEqualTo(PROVIDER_ID);

                        verify(providerRepository).findById(PROVIDER_ID);
                        verify(providerMapper).toProviderResponse(provider);
                }

                @Test
                @DisplayName("Should throw ResourceNotFoundException when provider does not exist")
                void getProviderProfile_NonExistingId_ThrowsException() {
                        when(providerRepository.findById(NON_EXISTENT_ID)).thenReturn(Optional.empty());

                        assertThatThrownBy(() -> providerService.getProviderProfile(NON_EXISTENT_ID))
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("Provider not found with id: " + NON_EXISTENT_ID);

                        verify(providerRepository).findById(NON_EXISTENT_ID);
                        verify(providerMapper, never()).toProviderResponse(any());
                }
        }

        @Nested
        @DisplayName("Update Provider Profile Tests")
        class UpdateProviderProfileTests {

                @Test
                @DisplayName("Should update all fields when request has all fields")
                void updateProviderProfile_AllFields_UpdatesEverything() {
                        ServiceType serviceType = provider.getServiceType();
                        ProviderProfileRequest request = ProviderProfileRequest.builder()
                                        .name("Updated Provider")
                                        // .email("updated@example.com")
                                        .phoneNumber("01234567890")
                                        .bio("Updated bio")
                                        .workLocation("Nasr City, Cairo")
                                        // .serviceTypeId(10L)
                                        .price(120.0)
                                        .priceType("HOUR")
                                        .serviceAreaRadius(5.5)
                                        .emergencyEnabled(true)
                                        .location(LocationDTO.builder().latitude(30.0).longitude(31.0).build())
                                        .build();

                        when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
                        when(providerRepository.save(any(Provider.class))).thenReturn(provider);
                        when(providerMapper.toProviderResponse(any(Provider.class))).thenReturn(providerResponse);

                        ProviderResponse result = providerService.updateProviderProfile(PROVIDER_ID, request);

                        assertThat(result).isNotNull();
                        assertThat(provider.getName()).isEqualTo(request.getName());
                        // assertThat(provider.getEmail()).isEqualTo(request.getEmail());
                        assertThat(provider.getPhoneNumber()).isEqualTo(request.getPhoneNumber());
                        assertThat(provider.getBio()).isEqualTo(request.getBio());
                        assertThat(provider.getWorkLocation()).isEqualTo(request.getWorkLocation());
                        assertThat(provider.getPrice()).isEqualTo(request.getPrice());
                        assertThat(provider.getPriceType()).isEqualTo(PriceType.HOUR);
                        assertThat(provider.getServiceAreaRadius()).isEqualTo(request.getServiceAreaRadius());
                        assertThat(provider.getEmergencyEnabled()).isEqualTo(request.getEmergencyEnabled());

                        verify(locationService).updateProviderLocation(eq(PROVIDER_ID), eq(request.getLocation()));
                        verify(providerRepository).save(provider);
                }

                @Test
                @DisplayName("Should throw BadRequestException when price type is invalid")
                void updateProviderProfile_InvalidPriceType_ThrowsException() {
                        ProviderProfileRequest request = ProviderProfileRequest.builder()
                                        .priceType("INVALID")
                                        .build();

                        when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));

                        assertThatThrownBy(() -> providerService.updateProviderProfile(PROVIDER_ID, request))
                                        .isInstanceOf(BadRequestException.class)
                                        .hasMessageContaining("Invalid price type");

                        verify(providerRepository, never()).save(any(Provider.class));
                }

                @Test
                @DisplayName("Should throw ResourceNotFoundException when service type not found")
                void updateProviderProfile_ServiceTypeNotFound_ThrowsException() {
                        ProviderProfileRequest request = ProviderProfileRequest.builder()
                                        .serviceTypeId(99L)
                                        .build();

                        when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
                        // when(serviceTypeRepository.findById(99L)).thenReturn(Optional.empty());

                        assertThatThrownBy(() -> providerService.updateProviderProfile(PROVIDER_ID, request))
                                        .isInstanceOf(BadRequestException.class)
                                        .hasMessageContaining("Service type cannot be changed directly");

                        verify(providerRepository, never()).save(any(Provider.class));
                }

                @Test
                @DisplayName("Should throw ResourceNotFoundException when provider does not exist")
                void updateProviderProfile_NonExistingId_ThrowsException() {
                        ProviderProfileRequest request = ProviderProfileRequest.builder()
                                        .name("Updated Provider")
                                        .build();

                        when(providerRepository.findById(NON_EXISTENT_ID)).thenReturn(Optional.empty());

                        assertThatThrownBy(() -> providerService.updateProviderProfile(NON_EXISTENT_ID, request))
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("Provider not found with id: " + NON_EXISTENT_ID);

                        verify(providerRepository).findById(NON_EXISTENT_ID);
                        verify(providerRepository, never()).save(any(Provider.class));
                        verify(locationService, never()).updateProviderLocation(any(), any());
                }
        }

        @Nested
        @DisplayName("Update Profile Picture Tests")
        class UpdateProfilePictureTests {

                @Test
                @DisplayName("Should update profile picture and delete old image")
                void updateProfilePicture_ValidProvider_UpdatesImage() throws IOException {
                        MockMultipartFile file = new MockMultipartFile(
                                        "file",
                                        "profile.jpg",
                                        "image/jpeg",
                                        "test".getBytes());
                        Provider updatedProvider = TestDataFactory.createProvider(PROVIDER_ID);

                        when(providerRepository.findById(PROVIDER_ID))
                                        .thenReturn(Optional.of(provider))
                                        .thenReturn(Optional.of(updatedProvider));
                        when(fileStorageService.storeFile(eq(file), eq(PROVIDER_ID.toString())))
                                        .thenReturn("new-url.jpg");
                        when(providerMapper.toProviderResponse(updatedProvider)).thenReturn(providerResponse);

                        ProviderResponse result = providerService.updateProfilePicture(PROVIDER_ID, file);

                        assertThat(result).isNotNull();
                        verify(providerRepository).updateProfileImage(PROVIDER_ID, "new-url.jpg");
                        verify(fileStorageService).deleteFile(provider.getProfileImage());
                }

                @Test
                @DisplayName("Should throw ResourceNotFoundException when provider not found")
                void updateProfilePicture_ProviderNotFound_ThrowsException() {
                        MockMultipartFile file = new MockMultipartFile(
                                        "file",
                                        "profile.jpg",
                                        "image/jpeg",
                                        "test".getBytes());
                        when(providerRepository.findById(NON_EXISTENT_ID)).thenReturn(Optional.empty());

                        assertThatThrownBy(() -> providerService.updateProfilePicture(NON_EXISTENT_ID, file))
                                        .isInstanceOf(ResourceNotFoundException.class)
                                        .hasMessageContaining("Provider not found");
                }
        }

        @Nested
        @DisplayName("Working Day Tests")
        class WorkingDayTests {

                @Test
                @DisplayName("Should throw BadRequestException when start time is after end time")
                void addWorkingDay_InvalidTime_ThrowsException() {
                        WorkingDayRequest request = WorkingDayRequest.builder()
                                        .date(LocalDate.now().plusDays(1))
                                        .startTime(LocalTime.of(10, 0))
                                        .endTime(LocalTime.of(9, 0))
                                        .build();

                        when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));

                        assertThatThrownBy(() -> providerService.addWorkingDay(PROVIDER_ID, request))
                                        .isInstanceOf(BadRequestException.class)
                                        .hasMessageContaining("Start time must be before end time");

                        verify(workingDayRepository, never()).save(any());
                }

                @Test
                @DisplayName("Should add working day and time slot successfully")
                void addWorkingDay_ValidRequest_AddsSchedule() {
                        WorkingDayRequest request = WorkingDayRequest.builder()
                                        .date(LocalDate.now().plusDays(1))
                                        .startTime(LocalTime.of(9, 0))
                                        .endTime(LocalTime.of(12, 0))
                                        .build();

                        Schedule schedule = Schedule.builder()
                                        .workingDays(new ArrayList<>())
                                        .timeSlots(new ArrayList<>())
                                        .build();
                        provider.setSchedule(schedule);

                        WorkingDay workingDay = WorkingDay.builder()
                                        .id(5L)
                                        .date(request.getDate())
                                        .startTime(request.getStartTime())
                                        .endTime(request.getEndTime())
                                        .schedule(schedule)
                                        .build();

                        TimeSlot timeSlot = TimeSlot.builder()
                                        .id(8L)
                                        .date(request.getDate())
                                        .startTime(request.getStartTime())
                                        .endTime(request.getEndTime())
                                        .status(TimeSlotStatus.AVAILABLE)
                                        .schedule(schedule)
                                        .build();

                        ScheduleResponse scheduleResponse = ScheduleResponse.builder()
                                        .id(1L)
                                        .workingDays(List.of())
                                        .timeSlots(List.of())
                                        .build();

                        when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
                        when(workingDayRepository.save(any(WorkingDay.class))).thenReturn(workingDay);
                        when(timeSlotRepository.save(any(TimeSlot.class))).thenReturn(timeSlot);
                        when(providerRepository.save(any(Provider.class))).thenReturn(provider);
                        when(scheduleMapper.toScheduleResponse(any(Schedule.class))).thenReturn(scheduleResponse);

                        ScheduleResponse result = providerService.addWorkingDay(PROVIDER_ID, request);

                        assertThat(result).isNotNull();
                        assertThat(schedule.getWorkingDays()).hasSize(1);
                        assertThat(schedule.getTimeSlots()).hasSize(1);
                }
        }

        @Nested
        @DisplayName("Get Time Slot Tests")
        class GetTimeSlotTests {

                @Test
                @DisplayName("Should throw BadRequestException when time slot belongs to different schedule")
                void getTimeSlot_MismatchedSchedule_ThrowsException() {
                        Schedule schedule = Schedule.builder().id(1L).build();
                        provider.setSchedule(schedule);
                        TimeSlot timeSlot = TimeSlot.builder()
                                        .id(3L)
                                        .schedule(Schedule.builder().id(2L).build())
                                        .build();

                        when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
                        when(timeSlotRepository.findById(3L)).thenReturn(Optional.of(timeSlot));

                        assertThatThrownBy(() -> providerService.getTimeSlot(PROVIDER_ID, 3L))
                                        .isInstanceOf(BadRequestException.class)
                                        .hasMessageContaining("Time slot does not belong to this provider");
                }
        }


        @Test
        void search_shouldReturnPaginatedResults() {

                List<SearchResponse> fullList = List.of(
                                new SearchResponse(),
                                new SearchResponse(),
                                new SearchResponse());

                Pageable pageable = PageRequest.of(0, 2);

                when(searchCacheService.searchList(
                                any(), any(), any(), any(), any(), any(), any())).thenReturn(fullList);

                Page<SearchResponse> result = providerService.search(
                                "test",
                                1L,
                                "cat",
                                1L,
                                10.0,
                                "rating",
                                pageable);

                assertThat(result.getContent()).hasSize(2);
                assertThat(result.getTotalElements()).isEqualTo(3);
        }

        @Test
        @DisplayName("topRatedNearMe should return mapped results")
        void topRatedNearMe_shouldReturnMappedResults() {

                Pageable pageable = PageRequest.of(0, 10);

                SearchResponse response = SearchResponse.builder()
                                .id(1L)
                                .name("Ibrahim Nasser")
                                .serviceType("Drain Cleaning")
                                .categoryName("Plumbing")
                                .averageRating(4.8)
                                .estimatedArrivalTime(15)
                                .build();

                response.setDistance(0.39);

                when(searchCacheService.topRatedNearMe(1L, 10.0))
                                .thenReturn(List.of(response));

                Page<SearchResponse> result = providerService.topRatedNearMe(
                                1L,
                                10.0,
                                pageable);

                assertThat(result.getContent()).hasSize(1);
                assertThat(result.getTotalElements()).isEqualTo(1);
                assertThat(result.getContent().get(0).getName())
                                .isEqualTo("Ibrahim Nasser");

                verify(searchCacheService)
                                .topRatedNearMe(1L, 10.0);
        }


    @Nested
    @DisplayName("Delete Profile Picture Tests")
    class DeleteProfilePictureTests {

        @Test
        @DisplayName("Should delete profile picture successfully")
        void deleteProfilePicture_ValidProvider_DeletesImage() {
            provider.setProfileImage("old-image.jpg");
            
            when(providerRepository.findById(PROVIDER_ID))
                    .thenReturn(Optional.of(provider))
                    .thenReturn(Optional.of(provider));
            doNothing().when(fileStorageService).deleteFile("old-image.jpg");
            when(providerMapper.toProviderResponse(any(Provider.class))).thenReturn(providerResponse);

            ProviderResponse result = providerService.deleteProfilePicture(PROVIDER_ID);

            assertThat(result).isNotNull();
            verify(fileStorageService).deleteFile("old-image.jpg");
            verify(providerRepository).updateProfileImage(PROVIDER_ID, null);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when provider not found")
        void deleteProfilePicture_ProviderNotFound_ThrowsException() {
            when(providerRepository.findById(NON_EXISTENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> providerService.deleteProfilePicture(NON_EXISTENT_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Provider not found");
        }

        @Test
        @DisplayName("Should handle null profile image gracefully")
        void deleteProfilePicture_NullProfileImage_DoesNotDelete() {
            provider.setProfileImage(null);
            
            when(providerRepository.findById(PROVIDER_ID))
                    .thenReturn(Optional.of(provider))
                    .thenReturn(Optional.of(provider));
            when(providerMapper.toProviderResponse(any(Provider.class))).thenReturn(providerResponse);

            ProviderResponse result = providerService.deleteProfilePicture(PROVIDER_ID);

            assertThat(result).isNotNull();
            verify(fileStorageService, never()).deleteFile(anyString());
            verify(providerRepository).updateProfileImage(PROVIDER_ID, null);
        }

        @Test
        @DisplayName("Should throw BadRequestException when file deletion fails")
        void deleteProfilePicture_FileDeletionFails_ThrowsException() {
            provider.setProfileImage("old-image.jpg");
            
            when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
            doThrow(new RuntimeException("Deletion failed")).when(fileStorageService).deleteFile("old-image.jpg");

            assertThatThrownBy(() -> providerService.deleteProfilePicture(PROVIDER_ID))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Failed to delete profile picture");
        }
    }

    @Nested
    @DisplayName("All Providers Tests")
    class AllProvidersTests {

        @Test
        @DisplayName("Should return all providers")
        void allProviders_ReturnsList() {
        List<Provider> providers = List.of(provider, TestDataFactory.createProvider(2L));
            
            when(providerRepository.findAll()).thenReturn(providers);
            when(providerMapper.toProviderSummaryResponse(any(Provider.class)))
                    .thenReturn(com.aykhedma.dto.response.ProviderSummaryResponse.builder().id(PROVIDER_ID).build());

            List<com.aykhedma.dto.response.ProviderSummaryResponse> result = providerService.allProviders();

            assertThat(result).isNotNull();
            assertThat(result).hasSize(2);
            verify(providerRepository).findAll();
        }

        @Test
        @DisplayName("Should return empty list when no providers exist")
        void allProviders_EmptyList_ReturnsEmpty() {
            when(providerRepository.findAll()).thenReturn(new ArrayList<>());

            List<com.aykhedma.dto.response.ProviderSummaryResponse> result = providerService.allProviders();

            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Get Schedule Tests")
    class GetScheduleTests {

        @Test
        @DisplayName("Should return schedule when provider exists")
        void getSchedule_ValidProvider_ReturnsSchedule() {
            Schedule schedule = Schedule.builder()
                    .id(1L)
                    .workingDays(new ArrayList<>())
                    .timeSlots(new ArrayList<>())
                    .build();
            provider.setSchedule(schedule);
            
            ScheduleResponse scheduleResponse = ScheduleResponse.builder()
                    .id(1L)
                    .workingDays(new ArrayList<>())
                    .timeSlots(new ArrayList<>())
                    .build();

            when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
            when(scheduleMapper.toScheduleResponse(schedule)).thenReturn(scheduleResponse);

            ScheduleResponse result = providerService.getSchedule(PROVIDER_ID);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Should return empty schedule when provider has no schedule")
        void getSchedule_NoSchedule_ReturnsEmptySchedule() {
            provider.setSchedule(null);
            
            when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));

            ScheduleResponse result = providerService.getSchedule(PROVIDER_ID);

            assertThat(result).isNotNull();
            assertThat(result.getWorkingDays()).isEmpty();
            assertThat(result.getTimeSlots()).isEmpty();
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when provider not found")
        void getSchedule_ProviderNotFound_ThrowsException() {
            when(providerRepository.findById(NON_EXISTENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> providerService.getSchedule(NON_EXISTENT_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Provider not found");
        }
    }

    @Nested
    @DisplayName("Weekly Schedule Tests")
    class WeeklyScheduleTests {

        @Test
        @DisplayName("Should return weekly schedule when provider exists")
        void getWeeklySchedule_ValidProvider_ReturnsWeeklySchedule() {
                Schedule schedule = Schedule.builder()
                                .id(1L)
                                .workingDays(new ArrayList<>())
                                .timeSlots(new ArrayList<>())
                                .build();
                provider.setSchedule(schedule);

                when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));

                WeeklyScheduleResponse result = providerService.getWeeklySchedule(PROVIDER_ID);

                assertThat(result).isNotNull();
                assertThat(result.getWorkingDays()).isNotNull();
        }

        @Test
        @DisplayName("Should return empty weekly schedule when provider has no schedule")
        void getWeeklySchedule_NoSchedule_ReturnsEmpty() {
                provider.setSchedule(null);

                when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));

                WeeklyScheduleResponse result = providerService.getWeeklySchedule(PROVIDER_ID);

                assertThat(result).isNotNull();
                assertThat(result.getWorkingDays()).isEmpty();
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when provider not found")
        void getWeeklySchedule_ProviderNotFound_ThrowsException() {
                when(providerRepository.findById(NON_EXISTENT_ID)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> providerService.getWeeklySchedule(NON_EXISTENT_ID))
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("Provider not found");
        }
    }

    @Nested
    @DisplayName("Get Time Slots By Date Tests")
    class GetTimeSlotsByDateTests {

        @Test
        @DisplayName("Should return time slots for a specific date")
        void getTimeSlotsByDate_ValidDate_ReturnsSlots() {
            LocalDate date = LocalDate.now().plusDays(1);
            Schedule schedule = Schedule.builder().id(1L).build();
            provider.setSchedule(schedule);
            
            TimeSlot slot = TimeSlot.builder()
                    .id(1L)
                    .date(date)
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(12, 0))
                    .status(TimeSlotStatus.AVAILABLE)
                    .schedule(schedule)
                    .build();

            when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
            when(timeSlotRepository.findByScheduleIdAndDate(schedule.getId(), date))
                    .thenReturn(List.of(slot));

            List<ScheduleResponse.TimeSlotResponse> result = providerService.getTimeSlotsByDate(PROVIDER_ID, date);

            assertThat(result).isNotNull();
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("Should return empty list when no slots available")
        void getTimeSlotsByDate_NoSlots_ReturnsEmpty() {
            LocalDate date = LocalDate.now().plusDays(1);
            Schedule schedule = Schedule.builder().id(1L).build();
            provider.setSchedule(schedule);

            when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
            when(timeSlotRepository.findByScheduleIdAndDate(schedule.getId(), date))
                    .thenReturn(new ArrayList<>());

            List<ScheduleResponse.TimeSlotResponse> result = providerService.getTimeSlotsByDate(PROVIDER_ID, date);

            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when provider not found")
        void getTimeSlotsByDate_ProviderNotFound_ThrowsException() {
            when(providerRepository.findById(NON_EXISTENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> providerService.getTimeSlotsByDate(NON_EXISTENT_ID, LocalDate.now()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Provider not found");
        }
    }

    @Nested
    @DisplayName("Available Time Slots Tests")
    class AvailableTimeSlotsTests {

        @Test
        @DisplayName("Should return available time slots for a date")
        void getAvailableTimeSlots_ValidDate_ReturnsAvailableSlots() {
            LocalDate date = LocalDate.now().plusDays(1);
            Schedule schedule = Schedule.builder().id(1L).build();
            provider.setSchedule(schedule);
            
            TimeSlot slot = TimeSlot.builder()
                    .id(1L)
                    .date(date)
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(12, 0))
                    .status(TimeSlotStatus.AVAILABLE)
                    .schedule(schedule)
                    .build();

            when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
            when(timeSlotRepository.findByScheduleIdAndDateAndStatus(schedule.getId(), date, TimeSlotStatus.AVAILABLE))
                    .thenReturn(List.of(slot));

            List<ScheduleResponse.TimeSlotResponse> result = providerService.getAvailableTimeSlots(PROVIDER_ID, date);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should return empty list when no available slots")
        void getAvailableTimeSlots_NoAvailableSlots_ReturnsEmpty() {
            LocalDate date = LocalDate.now().plusDays(1);
            Schedule schedule = Schedule.builder().id(1L).build();
            provider.setSchedule(schedule);

            when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
            when(timeSlotRepository.findByScheduleIdAndDateAndStatus(schedule.getId(), date, TimeSlotStatus.AVAILABLE))
                    .thenReturn(new ArrayList<>());

            List<ScheduleResponse.TimeSlotResponse> result = providerService.getAvailableTimeSlots(PROVIDER_ID, date);

            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Upload Document Tests")
    class UploadDocumentTests {

            @Test
            @DisplayName("Should upload document successfully")
            void uploadDocument_ValidProvider_UploadsDocument() throws IOException {
                    MockMultipartFile file = new MockMultipartFile(
                                    "file",
                                    "document.pdf",
                                    "application/pdf",
                                    "test".getBytes());

                    Document document = Document.builder()
                                    .id(1L)
                                    .title("document.pdf")
                                    .type("NATIONAL_ID")
                                    .filePath("/uploads/1/document.pdf")
                                    .provider(provider)
                                    .build();

                    when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
                    when(fileStorageService.storeFile(eq(file), eq(PROVIDER_ID.toString())))
                                    .thenReturn("/uploads/1/document.pdf");
                    when(documentRepository.save(any(Document.class))).thenReturn(document);

                    DocumentResponse result = providerService.uploadDocument(PROVIDER_ID, file, "NATIONAL_ID");

                    assertThat(result).isNotNull();
                    assertThat(result.getId()).isEqualTo(1L);
                    assertThat(result.getType()).isEqualTo("NATIONAL_ID");
                    verify(documentRepository).save(any(Document.class));
            }

            @Test
            @DisplayName("Should throw ResourceNotFoundException when provider not found")
            void uploadDocument_ProviderNotFound_ThrowsException() throws IOException {
                    MockMultipartFile file = new MockMultipartFile(
                                    "file",
                                    "document.pdf",
                                    "application/pdf",
                                    "test".getBytes());

                    when(providerRepository.findById(NON_EXISTENT_ID)).thenReturn(Optional.empty());

                    assertThatThrownBy(() -> providerService.uploadDocument(NON_EXISTENT_ID, file, "NATIONAL_ID"))
                                    .isInstanceOf(ResourceNotFoundException.class)
                                    .hasMessageContaining("Provider not found");
            }
    }
    @Nested
    @DisplayName("Get Provider Documents Tests")
    class GetProviderDocumentsTests {

        @Test
        @DisplayName("Should return provider documents")
        void getProviderDocuments_ValidProvider_ReturnsDocuments() {
                Document doc1 = Document.builder()
                                .id(1L)
                                .title("doc1.pdf")
                                .type("CERTIFICATE") 
                                .filePath("/uploads/1/doc1.pdf")
                                .provider(provider)
                                .build();
                Document doc2 = Document.builder()
                                .id(2L)
                                .title("doc2.pdf")
                                .type("LICENSE") 
                                .filePath("/uploads/1/doc2.pdf")
                                .provider(provider)
                                .build();

                when(documentRepository.findByProviderId(PROVIDER_ID)).thenReturn(List.of(doc1, doc2));

                List<DocumentResponse> result = providerService.getProviderDocuments(PROVIDER_ID);

                assertThat(result).isNotNull();
                assertThat(result).hasSize(2);
                assertThat(result.get(0).getType()).isEqualTo("CERTIFICATE");
                assertThat(result.get(1).getType()).isEqualTo("LICENSE");
        }

        @Test
        @DisplayName("Should return empty list when no documents")
        void getProviderDocuments_NoDocuments_ReturnsEmpty() {
                when(documentRepository.findByProviderId(PROVIDER_ID)).thenReturn(new ArrayList<>());

                List<DocumentResponse> result = providerService.getProviderDocuments(PROVIDER_ID);

                assertThat(result).isNotNull();
                assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Delete Document Tests")
    class DeleteDocumentTests {

        @Test
        @DisplayName("Should delete document successfully")
        void deleteDocument_ValidDocument_DeletesDocument() {
                Document document = Document.builder()
                                .id(1L)
                                .title("doc.pdf")
                                .type("CERTIFICATE")
                                .filePath("/uploads/1/doc.pdf")
                                .provider(provider)
                                .build();

                when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

                ProfileResponse result = providerService.deleteDocument(PROVIDER_ID, 1L);

                assertThat(result).isNotNull();
                assertThat(result.isSuccess()).isTrue();
                assertThat(result.getMessage()).contains("Document deleted successfully");
                verify(documentRepository).delete(document);
                verify(fileStorageService).deleteFile("/uploads/1/doc.pdf");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when document not found")
        void deleteDocument_DocumentNotFound_ThrowsException() {
            when(documentRepository.findById(NON_EXISTENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> providerService.deleteDocument(PROVIDER_ID, NON_EXISTENT_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Document not found");
        }

        @Test
        @DisplayName("Should throw BadRequestException when document belongs to different provider")
        void deleteDocument_DocumentBelongsToDifferentProvider_ThrowsException() {
            Provider otherProvider = TestDataFactory.createProvider(2L);
            Document document = Document.builder()
                    .id(1L)
                    .provider(otherProvider)
                    .build();

            when(documentRepository.findById(1L)).thenReturn(Optional.of(document));

            assertThatThrownBy(() -> providerService.deleteDocument(PROVIDER_ID, 1L))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Document does not belong to this provider");
        }
    }

    @Nested
    @DisplayName("Get Verification Status Tests")
    class GetVerificationStatusTests {

            @Test
            @DisplayName("Should return verification status")
            void getVerificationStatus_ValidProvider_ReturnsStatus() {
                    provider.setVerificationStatus(VerificationStatus.VERIFIED);

                    when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));

                    VerificationStatus result = providerService.getVerificationStatus(PROVIDER_ID);

                    assertThat(result).isEqualTo(VerificationStatus.VERIFIED);
            }

            @Test
            @DisplayName("Should throw ResourceNotFoundException when provider not found")
            void getVerificationStatus_ProviderNotFound_ThrowsException() {
                    when(providerRepository.findById(NON_EXISTENT_ID)).thenReturn(Optional.empty());

                    assertThatThrownBy(() -> providerService.getVerificationStatus(NON_EXISTENT_ID))
                                    .isInstanceOf(ResourceNotFoundException.class)
                                    .hasMessageContaining("Provider not found");
            }
    }

    @Nested
    @DisplayName("Get Upcoming Available Slots Tests")
    class GetUpcomingAvailableSlotsTests {

        @Test
        @DisplayName("Should return upcoming available slots")
        void getUpcomingAvailableSlots_ValidProvider_ReturnsSlots() {
            Schedule schedule = Schedule.builder().id(1L).build();
            provider.setSchedule(schedule);
            
            TimeSlot slot = TimeSlot.builder()
                    .id(1L)
                    .date(LocalDate.now().plusDays(1))
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(12, 0))
                    .status(TimeSlotStatus.AVAILABLE)
                    .schedule(schedule)
                    .build();

            when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
            when(timeSlotRepository.findByScheduleIdAndDateBetweenAndStatus(
                    eq(schedule.getId()), any(LocalDate.class), any(LocalDate.class), eq(TimeSlotStatus.AVAILABLE)))
                    .thenReturn(List.of(slot));

            List<ScheduleResponse.TimeSlotResponse> result = providerService.getUpcomingAvailableSlots(PROVIDER_ID, 7);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should use default 7 days when days is null or invalid")
        void getUpcomingAvailableSlots_InvalidDays_UsesDefault() {
            Schedule schedule = Schedule.builder().id(1L).build();
            provider.setSchedule(schedule);

            when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
            when(timeSlotRepository.findByScheduleIdAndDateBetweenAndStatus(
                    eq(schedule.getId()), any(LocalDate.class), any(LocalDate.class), eq(TimeSlotStatus.AVAILABLE)))
                    .thenReturn(new ArrayList<>());

            List<ScheduleResponse.TimeSlotResponse> result = providerService.getUpcomingAvailableSlots(PROVIDER_ID, 0);

            assertThat(result).isNotNull();
            // Verify that the method was called with days range
        }
    }

    @Nested
    @DisplayName("Available Time Slots For Date Range Tests")
    class GetAvailableTimeSlotsForDateRangeTests {

        @Test
        @DisplayName("Should return available slots for date range")
        void getAvailableTimeSlotsForDateRange_ValidRange_ReturnsSlots() {
            LocalDate startDate = LocalDate.now().plusDays(1);
            LocalDate endDate = LocalDate.now().plusDays(3);
            Schedule schedule = Schedule.builder().id(1L).build();
            provider.setSchedule(schedule);
            
            TimeSlot slot = TimeSlot.builder()
                    .id(1L)
                    .date(startDate)
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(12, 0))
                    .status(TimeSlotStatus.AVAILABLE)
                    .schedule(schedule)
                    .build();

            when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
            when(timeSlotRepository.findByScheduleIdAndDateBetweenAndStatus(
                    eq(schedule.getId()), eq(startDate), eq(endDate), eq(TimeSlotStatus.AVAILABLE)))
                    .thenReturn(List.of(slot));

            List<ScheduleResponse.TimeSlotResponse> result = providerService.getAvailableTimeSlotsForDateRange(
                    PROVIDER_ID, startDate, endDate);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Should return empty list when no slots available")
        void getAvailableTimeSlotsForDateRange_NoSlots_ReturnsEmpty() {
            LocalDate startDate = LocalDate.now().plusDays(1);
            LocalDate endDate = LocalDate.now().plusDays(3);
            Schedule schedule = Schedule.builder().id(1L).build();
            provider.setSchedule(schedule);

            when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
            when(timeSlotRepository.findByScheduleIdAndDateBetweenAndStatus(
                    eq(schedule.getId()), eq(startDate), eq(endDate), eq(TimeSlotStatus.AVAILABLE)))
                    .thenReturn(new ArrayList<>());

            List<ScheduleResponse.TimeSlotResponse> result = providerService.getAvailableTimeSlotsForDateRange(
                    PROVIDER_ID, startDate, endDate);

            assertThat(result).isNotNull();
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Validate Half Hour Boundary Tests")
    class ValidateHalfHourBoundaryTests {

        @Test
        @DisplayName("Should pass when time is on half-hour boundary")
        void validateHalfHourBoundary_ValidTime_Passes() {
            LocalTime time = LocalTime.of(10, 0);
            providerService.validateHalfHourBoundary(time);
            // Should not throw exception
        }

        @Test
        @DisplayName("Should pass when time is on 30-minute boundary")
        void validateHalfHourBoundary_ValidThirtyMinute_Passes() {
            LocalTime time = LocalTime.of(10, 30);
            providerService.validateHalfHourBoundary(time);
            // Should not throw exception
        }

        @Test
        @DisplayName("Should throw BadRequestException when time is null")
        void validateHalfHourBoundary_NullTime_ThrowsException() {
            assertThatThrownBy(() -> providerService.validateHalfHourBoundary(null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Start time is required");
        }

        @Test
        @DisplayName("Should throw BadRequestException when time has seconds")
        void validateHalfHourBoundary_TimeWithSeconds_ThrowsException() {
            LocalTime time = LocalTime.of(10, 0, 30);
            
            assertThatThrownBy(() -> providerService.validateHalfHourBoundary(time))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("HH:mm format");
        }

        @Test
        @DisplayName("Should throw BadRequestException when time is not on 30-minute boundary")
        void validateHalfHourBoundary_InvalidMinute_ThrowsException() {
            LocalTime time = LocalTime.of(10, 15);
            
            assertThatThrownBy(() -> providerService.validateHalfHourBoundary(time))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("30-minute boundary");
        }
    }

    @Nested
    @DisplayName("Min/Max Time Helper Tests")
    class MinMaxTimeTests {

        @Test
        @DisplayName("Should return later time")
        void maxTime_ReturnsLaterTime() {
            LocalTime t1 = LocalTime.of(10, 0);
            LocalTime t2 = LocalTime.of(11, 0);
            
            LocalTime result = providerService.maxTime(t1, t2);
            
            assertThat(result).isEqualTo(t2);
        }

        @Test
        @DisplayName("Should return earlier time")
        void minTime_ReturnsEarlierTime() {
            LocalTime t1 = LocalTime.of(10, 0);
            LocalTime t2 = LocalTime.of(11, 0);
            
            LocalTime result = providerService.minTime(t1, t2);
            
            assertThat(result).isEqualTo(t1);
        }
    }

    @Nested
    @DisplayName("Remove Working Day Tests")
    class RemoveWorkingDayTests {

        @Test
        @DisplayName("Should remove working day successfully")
        void removeWorkingDay_ValidRequest_RemovesWorkingDay() {
            Schedule schedule = Schedule.builder()
                    .id(1L)
                    .workingDays(new ArrayList<>())
                    .timeSlots(new ArrayList<>())
                    .build();
            provider.setSchedule(schedule);
            
            LocalDate date = LocalDate.now().plusDays(1);
            WorkingDay workingDay = WorkingDay.builder()
                    .id(5L)
                    .date(date)
                    .schedule(schedule)
                    .build();
            
            TimeSlot timeSlot = TimeSlot.builder()
                    .id(8L)
                    .date(date)
                    .status(TimeSlotStatus.AVAILABLE)
                    .schedule(schedule)
                    .build();
            schedule.getWorkingDays().add(workingDay);
            schedule.getTimeSlots().add(timeSlot);
            
            ScheduleResponse scheduleResponse = ScheduleResponse.builder().id(1L).build();

            when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
            when(workingDayRepository.findById(5L)).thenReturn(Optional.of(workingDay));
            when(scheduleMapper.toScheduleResponse(any(Schedule.class))).thenReturn(scheduleResponse);

            ScheduleResponse result = providerService.removeWorkingDay(PROVIDER_ID, 5L);

            assertThat(result).isNotNull();
            verify(workingDayRepository).delete(workingDay);
            verify(timeSlotRepository).deleteAll(anyList());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when provider not found")
        void removeWorkingDay_ProviderNotFound_ThrowsException() {
            when(providerRepository.findById(NON_EXISTENT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> providerService.removeWorkingDay(NON_EXISTENT_ID, 5L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Provider not found");
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when working day not found")
        void removeWorkingDay_WorkingDayNotFound_ThrowsException() {
            Schedule schedule = Schedule.builder().id(1L).build();
            provider.setSchedule(schedule);

            when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
            when(workingDayRepository.findById(5L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> providerService.removeWorkingDay(PROVIDER_ID, 5L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Working day not found");
        }
    }

    @Nested
    @DisplayName("Update Working Day Tests")
    class UpdateWorkingDayTests {

        @Test
        @DisplayName("Should update working day successfully")
        void updateWorkingDay_ValidRequest_UpdatesWorkingDay() {
            Schedule schedule = Schedule.builder()
                    .id(1L)
                    .workingDays(new ArrayList<>())
                    .timeSlots(new ArrayList<>())
                    .build();
            provider.setSchedule(schedule);
            
            LocalDate date = LocalDate.now().plusDays(1);
            WorkingDay workingDay = WorkingDay.builder()
                    .id(5L)
                    .date(LocalDate.now().plusDays(2))
                    .startTime(LocalTime.of(9, 0))
                    .endTime(LocalTime.of(12, 0))
                    .schedule(schedule)
                    .build();
            schedule.getWorkingDays().add(workingDay);
            
            WorkingDayRequest request = WorkingDayRequest.builder()
                    .date(date)
                    .startTime(LocalTime.of(10, 0))
                    .endTime(LocalTime.of(14, 0))
                    .build();
            
            ScheduleResponse scheduleResponse = ScheduleResponse.builder().id(1L).build();

            when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
            when(workingDayRepository.findById(5L)).thenReturn(Optional.of(workingDay));
            when(workingDayRepository.save(any(WorkingDay.class))).thenReturn(workingDay);
            when(scheduleMapper.toScheduleResponse(any(Schedule.class))).thenReturn(scheduleResponse);

            ScheduleResponse result = providerService.updateWorkingDay(PROVIDER_ID, 5L, request);

            assertThat(result).isNotNull();
            assertThat(workingDay.getDate()).isEqualTo(date);
            assertThat(workingDay.getStartTime()).isEqualTo(LocalTime.of(10, 0));
            assertThat(workingDay.getEndTime()).isEqualTo(LocalTime.of(14, 0));
            verify(workingDayRepository).save(workingDay);
        }

        @Test
        @DisplayName("Should throw BadRequestException when start time is after end time")
        void updateWorkingDay_InvalidTime_ThrowsException() {
            Schedule schedule = Schedule.builder().id(1L).build();
            provider.setSchedule(schedule);
            
            WorkingDay workingDay = WorkingDay.builder()
                    .id(5L)
                    .schedule(schedule)
                    .build();
            
            WorkingDayRequest request = WorkingDayRequest.builder()
                    .date(LocalDate.now().plusDays(1))
                    .startTime(LocalTime.of(14, 0))
                    .endTime(LocalTime.of(10, 0))
                    .build();

            when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
            when(workingDayRepository.findById(5L)).thenReturn(Optional.of(workingDay));

            assertThatThrownBy(() -> providerService.updateWorkingDay(PROVIDER_ID, 5L, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Start time must be before end time");
        }

        @Test
        @DisplayName("Should throw BadRequestException for past date")
        void updateWorkingDay_PastDate_ThrowsException() {
            Schedule schedule = Schedule.builder().id(1L).build();
            provider.setSchedule(schedule);
            
            WorkingDay workingDay = WorkingDay.builder()
                    .id(5L)
                    .schedule(schedule)
                    .build();
            
            WorkingDayRequest request = WorkingDayRequest.builder()
                    .date(LocalDate.now().minusDays(1))
                    .startTime(LocalTime.of(10, 0))
                    .endTime(LocalTime.of(14, 0))
                    .build();

            when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
            when(workingDayRepository.findById(5L)).thenReturn(Optional.of(workingDay));

            assertThatThrownBy(() -> providerService.updateWorkingDay(PROVIDER_ID, 5L, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("past dates");
        }
    }
}

