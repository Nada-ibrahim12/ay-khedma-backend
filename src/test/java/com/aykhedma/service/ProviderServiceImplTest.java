package com.aykhedma.service;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.request.ProviderProfileRequest;
import com.aykhedma.dto.request.WorkingDayRequest;
import com.aykhedma.dto.response.ProviderResponse;
import com.aykhedma.dto.response.ScheduleResponse;
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
import com.aykhedma.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                    .email("updated@example.com")
                    .phoneNumber("01234567890")
                    .bio("Updated bio")
                    .serviceTypeId(10L)
                    .price(120.0)
                    .priceType("HOUR")
                    .serviceArea("Maadi")
                    .serviceAreaRadius(5.5)
                    .emergencyEnabled(true)
                    .location(LocationDTO.builder().latitude(30.0).longitude(31.0).build())
                    .build();

            when(providerRepository.findById(PROVIDER_ID)).thenReturn(Optional.of(provider));
            when(serviceTypeRepository.findById(request.getServiceTypeId())).thenReturn(Optional.of(serviceType));
            when(providerRepository.save(any(Provider.class))).thenReturn(provider);
            when(providerMapper.toProviderResponse(any(Provider.class))).thenReturn(providerResponse);

            ProviderResponse result = providerService.updateProviderProfile(PROVIDER_ID, request);

            assertThat(result).isNotNull();
            assertThat(provider.getName()).isEqualTo(request.getName());
            assertThat(provider.getEmail()).isEqualTo(request.getEmail());
            assertThat(provider.getPhoneNumber()).isEqualTo(request.getPhoneNumber());
            assertThat(provider.getBio()).isEqualTo(request.getBio());
            assertThat(provider.getPrice()).isEqualTo(request.getPrice());
            assertThat(provider.getPriceType()).isEqualTo(PriceType.HOUR);
            assertThat(provider.getServiceArea()).isEqualTo(request.getServiceArea());
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
            when(serviceTypeRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> providerService.updateProviderProfile(PROVIDER_ID, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Service type not found");

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
                    "test".getBytes()
            );
            Provider updatedProvider = TestDataFactory.createProvider(PROVIDER_ID);

            when(providerRepository.findById(PROVIDER_ID))
                    .thenReturn(Optional.of(provider))
                    .thenReturn(Optional.of(updatedProvider));
            when(fileStorageService.storeFile(eq(file), eq("profile-images"))).thenReturn("new-url.jpg");
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
                    "test".getBytes()
            );
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

    @Nested
    @DisplayName("Book Time Slot Tests")
    class BookTimeSlotTests {

        @Test
        @DisplayName("Should throw BadRequestException when duration exceeds slot")
        void bookTimeSlot_ExceedsDuration_ThrowsException() {
            TimeSlot timeSlot = TimeSlot.builder()
                    .id(1L)
                    .date(LocalDate.now().plusDays(1))
                    .startTime(LocalTime.of(10, 0))
                    .endTime(LocalTime.of(11, 0))
                    .status(TimeSlotStatus.AVAILABLE)
                    .schedule(Schedule.builder().build())
                    .build();

            when(timeSlotRepository.findById(1L)).thenReturn(Optional.of(timeSlot));

            assertThatThrownBy(() -> providerService.bookTimeSlot(1L, 90))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Booking duration exceeds available time slot");
        }
    }
}
