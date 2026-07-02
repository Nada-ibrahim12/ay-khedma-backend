package com.aykhedma.service;

import com.aykhedma.dto.request.*;
import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.response.EmergencyRequestResponse;
import com.aykhedma.dto.response.PriceRecommendationResponse;
import com.aykhedma.dto.response.ProviderResponseResponse;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.mapper.EmergencyRequestMapper;
import com.aykhedma.mapper.ProviderResponseMapper;
import com.aykhedma.model.emergency.EmergencyRequest;
import com.aykhedma.model.emergency.EmergencyRequestStatus;
import com.aykhedma.model.emergency.ProviderResponse;
import com.aykhedma.model.emergency.ProviderResponseType;
import com.aykhedma.model.location.Location;
import com.aykhedma.model.notification.NotificationType;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.user.UserType;
import com.aykhedma.repository.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmergencyRequestServiceImpl Tests")
class EmergencyRequestServiceTest
{
    @Mock
    private EmergencyRequestRepository emergencyRequestRepository;
    @Mock
    private ProviderResponseRepository providerResponseRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ConsumerRepository consumerRepository;
    @Mock
    private ProviderRepository providerRepository;
    @Mock
    private ServiceTypeRepository serviceTypeRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private EmergencyRequestMapper emergencyRequestMapper;
    @Mock
    private ProviderResponseMapper providerResponseMapper;
    @Mock
    private Scheduler scheduler;
    @Mock
    private GoogleMapsService googleMapsService;
    @Mock
    private RestTemplate restTemplate;
    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;
    @Mock
    private NotificationFactory notificationFactory;
    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private EmergencyRequestServiceImpl emergencyRequestService;

    @Captor
    private ArgumentCaptor<EmergencyRequest> emergencyRequestCaptor;
    @Captor
    private ArgumentCaptor<ProviderResponse> providerResponseCaptor;
    @Captor
    private ArgumentCaptor<Location> locationCaptor;
    @Captor
    private ArgumentCaptor<Provider> providerCaptor;
    @Captor
    private ArgumentCaptor<Consumer> consumerCaptor;
    @Captor
    private ArgumentCaptor<JobDetail> jobDetailCaptor;
    @Captor
    private ArgumentCaptor<Trigger> triggerCaptor;
    @Captor
    private ArgumentCaptor<JobKey> jobKeyCaptor;

    private Consumer consumer;
    private Provider provider;
    private ServiceType serviceType;
    private Location location;
    private LocationDTO locationDTO;
    private GoogleMapsService.LocationDetails locationDetails;
    private EmergencyRequest emergencyRequest;
    private ProviderResponse providerResponse;

    @BeforeEach
    void setUp()
    {
        LocalDateTime now = LocalDateTime.now();

        serviceType = ServiceType.builder()
                .id(1L)
                .name("Plumbing")
                .build();

        location = Location.builder()
                .id(1L)
                .latitude(30.0)
                .longitude(31.0)
                .address("Test Address")
                .area("Test Area")
                .city("Test City")
                .build();

        locationDTO = LocationDTO.builder()
                .latitude(30.0)
                .longitude(31.0)
                .build();

        locationDetails = new GoogleMapsService.LocationDetails();
        locationDetails.setAddress("Test Address");
        locationDetails.setArea("Test Area");
        locationDetails.setCity("Test City");

        consumer = Consumer.builder()
                .id(2L)
                .role(UserType.CONSUMER)
                .name("Consumer One")
                .location(location)
                .phoneNumber("123456789")
                .totalBookings(0)
                .cancelledBookings(0)
                .build();

        provider = Provider.builder()
                .id(1L)
                .role(UserType.PROVIDER)
                .name("Provider One")
                .location(location)
                .serviceType(serviceType)
                .totalRequests(0)
                .totalBookings(0)
                .completedJobs(0)
                .cancelledBookings(0)
                .build();

        emergencyRequest = EmergencyRequest.builder()
                .id(10L)
                .consumer(consumer)
                .serviceType(serviceType)
                .location(location)
                .price(100.0)
                .description("Emergency plumbing")
                .searchRadius(5)
                .status(EmergencyRequestStatus.BROADCASTING)
                .createdAt(now)
                .build();

        providerResponse = ProviderResponse.builder()
                .id(20L)
                .provider(provider)
                .emergencyRequest(emergencyRequest)
                .responseType(ProviderResponseType.NO_RESPONSE)
                .responseTime(now)
                .build();
    }

    @Nested
    @DisplayName("Get Current Emergency Request Tests")
    class GetCurrentEmergencyRequestTest
    {
        @Test
        @DisplayName("Successfully Get Current Emergency Request")
        void getCurrentEmergencyRequestSuccessTest() throws SchedulerException
        {
            when(consumerRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(emergencyRequestRepository.findTopByConsumerIdAndStatusInOrderByCreatedAtDesc(
                    consumer.getId(), List.of(EmergencyRequestStatus.BROADCASTING, EmergencyRequestStatus.WAITING_ACCEPTANCE)))
                    .thenReturn(emergencyRequest);
            when(scheduler.checkExists(any(JobKey.class))).thenReturn(false);
            when(emergencyRequestMapper.toEmergencyRequestResponse(emergencyRequest))
                    .thenReturn(EmergencyRequestResponse.builder().id(emergencyRequest.getId()).build());

            EmergencyRequestResponse response = emergencyRequestService.getCurrentEmergencyRequest(consumer.getId());

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(emergencyRequest.getId());
            verify(scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
        }

        @Test
        @DisplayName("Throw ResourceNotFoundException When No Ongoing Request Exists")
        void getCurrentEmergencyRequestNoOngoingTest()
        {
            when(consumerRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(emergencyRequestRepository.findTopByConsumerIdAndStatusInOrderByCreatedAtDesc(
                    consumer.getId(), List.of(EmergencyRequestStatus.BROADCASTING, EmergencyRequestStatus.WAITING_ACCEPTANCE)))
                    .thenReturn(null);

            assertThatThrownBy(() -> emergencyRequestService.getCurrentEmergencyRequest(consumer.getId()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("No currently ongoing emergency request found");
        }
    }

    @Nested
    @DisplayName("Get Emergency Request Price Recommendation Tests")
    class GetEmergencyRequestPriceRecommendationTest
    {
        @Test
        @DisplayName("Successfully Get Price Recommendation")
        void getPriceRecommendationSuccessTest()
        {
            PriceRecommendationRequest request = PriceRecommendationRequest.builder()
                    .serviceTypeId(serviceType.getId())
                    .location(locationDTO)
                    .build();

            when(consumerRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(serviceTypeRepository.findById(serviceType.getId())).thenReturn(Optional.of(serviceType));
            when(googleMapsService.getLocationDetails(locationDTO.getLatitude(), locationDTO.getLongitude()))
                    .thenReturn(locationDetails);
            when(providerRepository.getAveragePrice(serviceType, locationDetails.getArea())).thenReturn(80.0);

            PriceRecommendationResponse response = emergencyRequestService.getEmergencyRequestPriceRecommendation(
                    consumer.getId(), request);

            assertThat(response).isNotNull();
            assertThat(response.getPrice()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("Return Null Price When Area Is Null")
        void getPriceRecommendationAreaNullTest()
        {
            PriceRecommendationRequest request = PriceRecommendationRequest.builder()
                    .serviceTypeId(serviceType.getId())
                    .location(locationDTO)
                    .build();

            when(consumerRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(serviceTypeRepository.findById(serviceType.getId())).thenReturn(Optional.of(serviceType));
            GoogleMapsService.LocationDetails detailsWithNullArea = new GoogleMapsService.LocationDetails();
            detailsWithNullArea.setAddress("Address");
            detailsWithNullArea.setArea(null);
            when(googleMapsService.getLocationDetails(locationDTO.getLatitude(), locationDTO.getLongitude()))
                    .thenReturn(detailsWithNullArea);

            PriceRecommendationResponse response = emergencyRequestService.getEmergencyRequestPriceRecommendation(
                    consumer.getId(), request);

            assertThat(response.getPrice()).isNull();
        }

        @Test
        @DisplayName("Return Null Price When Average Price Is Null")
        void getPriceRecommendationAveragePriceNullTest()
        {
            PriceRecommendationRequest request = PriceRecommendationRequest.builder()
                    .serviceTypeId(serviceType.getId())
                    .location(locationDTO)
                    .build();

            when(consumerRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(serviceTypeRepository.findById(serviceType.getId())).thenReturn(Optional.of(serviceType));
            when(googleMapsService.getLocationDetails(locationDTO.getLatitude(), locationDTO.getLongitude()))
                    .thenReturn(locationDetails);
            when(providerRepository.getAveragePrice(serviceType, locationDetails.getArea())).thenReturn(null);

            PriceRecommendationResponse response = emergencyRequestService.getEmergencyRequestPriceRecommendation(
                    consumer.getId(), request);

            assertThat(response.getPrice()).isNull();
        }
    }

    @Nested
    @DisplayName("Request Emergency Request Tests")
    class RequestEmergencyRequestTest
    {
        @Test
        @DisplayName("Successfully Request Emergency Request")
        void requestEmergencyRequestSuccessTest() throws SchedulerException
        {
            EmergencyRequestRequest request = EmergencyRequestRequest.builder()
                    .serviceTypeId(serviceType.getId())
                    .location(locationDTO)
                    .price(150.0)
                    .description("Urgent repair")
                    .build();

            when(consumerRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(serviceTypeRepository.findById(serviceType.getId())).thenReturn(Optional.of(serviceType));
            when(emergencyRequestRepository.findTopByConsumerIdAndStatusInOrderByCreatedAtDesc(
                    consumer.getId(), List.of(EmergencyRequestStatus.BROADCASTING, EmergencyRequestStatus.WAITING_ACCEPTANCE)))
                    .thenReturn(null);
            when(googleMapsService.getLocationDetails(locationDTO.getLatitude(), locationDTO.getLongitude()))
                    .thenReturn(locationDetails);
            when(locationRepository.save(any(Location.class))).thenAnswer(i -> i.getArgument(0));
            when(emergencyRequestRepository.save(any(EmergencyRequest.class))).thenAnswer(i -> i.getArgument(0));
            when(emergencyRequestMapper.toEmergencyRequestResponse(any(EmergencyRequest.class)))
                    .thenReturn(EmergencyRequestResponse.builder().id(10L).build());

            EmergencyRequestResponse response = emergencyRequestService.requestEmergencyRequest(
                    consumer.getId(), request);

            assertThat(response).isNotNull();
            verify(locationRepository).save(locationCaptor.capture());
            Location savedLocation = locationCaptor.getValue();
            assertThat(savedLocation.getAddress()).isEqualTo("Test Address");
            assertThat(savedLocation.getArea()).isEqualTo("Test Area");

            verify(emergencyRequestRepository).save(emergencyRequestCaptor.capture());
            EmergencyRequest savedRequest = emergencyRequestCaptor.getValue();
            assertThat(savedRequest.getConsumer()).isEqualTo(consumer);
            assertThat(savedRequest.getServiceType()).isEqualTo(serviceType);
            assertThat(savedRequest.getPrice()).isEqualTo(150.0);
            assertThat(savedRequest.getStatus()).isEqualTo(EmergencyRequestStatus.BROADCASTING);
            assertThat(savedRequest.getSearchRadius()).isEqualTo(5);

            verify(scheduler).scheduleJob(any(JobDetail.class), any(Trigger.class));
        }

        @Test
        @DisplayName("Throw BadRequestException When Existing Ongoing Request Exists")
        void requestEmergencyRequestExistingOngoingTest()
        {
            EmergencyRequestRequest request = EmergencyRequestRequest.builder()
                    .serviceTypeId(serviceType.getId())
                    .location(locationDTO)
                    .build();

            when(consumerRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(serviceTypeRepository.findById(serviceType.getId())).thenReturn(Optional.of(serviceType));
            when(emergencyRequestRepository.findTopByConsumerIdAndStatusInOrderByCreatedAtDesc(
                    consumer.getId(), List.of(EmergencyRequestStatus.BROADCASTING, EmergencyRequestStatus.WAITING_ACCEPTANCE)))
                    .thenReturn(emergencyRequest);

            assertThatThrownBy(() -> emergencyRequestService.requestEmergencyRequest(consumer.getId(), request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("A currently ongoing emergency request for this consumer already exists");
        }
    }

    @Nested
    @DisplayName("Broadcast Emergency Request Tests")
    class BroadcastEmergencyRequestTest
    {
        @Test
        @DisplayName("Broadcast To Providers Within Radius Successfully")
        void broadcastEmergencyRequestSuccessTest()
        {
            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.of(emergencyRequest));
            when(providerRepository.findProvidersWithinRadius(serviceType.getId(), location.getCoordinates(), 5000))
                    .thenReturn(List.of(provider));
            when(providerResponseRepository.existsByEmergencyRequestIdAndProviderId(emergencyRequest.getId(), provider.getId()))
                    .thenReturn(false);
            when(providerResponseRepository.save(any(ProviderResponse.class))).thenAnswer(i -> i.getArgument(0));
            when(emergencyRequestRepository.save(any(EmergencyRequest.class))).thenAnswer(i -> i.getArgument(0));

            emergencyRequestService.broadcastEmergencyRequest(emergencyRequest.getId());

            verify(providerResponseRepository).save(providerResponseCaptor.capture());
            ProviderResponse savedResponse = providerResponseCaptor.getValue();
            assertThat(savedResponse.getProvider()).isEqualTo(provider);
            assertThat(savedResponse.getEmergencyRequest()).isEqualTo(emergencyRequest);
            assertThat(savedResponse.getResponseType()).isEqualTo(ProviderResponseType.NO_RESPONSE);
            assertThat(savedResponse.getEstimatedArrivalTime()).isNotNull();

            verify(notificationFactory).send(eq(provider.getId()), eq(NotificationType.EMERGENCY_OFFER), anyMap());
            verify(emergencyRequestRepository).save(emergencyRequestCaptor.capture());
            EmergencyRequest updatedRequest = emergencyRequestCaptor.getValue();
            assertThat(updatedRequest.getSearchRadius()).isEqualTo(10);
        }

        @Test
        @DisplayName("Set Status To WAITING_ACCEPTANCE When Radius Exceeds 50 And Responses Exist")
        void broadcastEmergencyRequestStatusWaitingAcceptanceTest() throws SchedulerException
        {
            emergencyRequest.setSearchRadius(50);
            List<ProviderResponse> responses = List.of(providerResponse);
            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.of(emergencyRequest));
            when(providerRepository.findProvidersWithinRadius(anyLong(), any(), anyDouble()))
                    .thenReturn(List.of(provider));
            when(providerResponseRepository.existsByEmergencyRequestIdAndProviderId(anyLong(), anyLong()))
                    .thenReturn(false);
            when(providerResponseRepository.save(any(ProviderResponse.class))).thenAnswer(i -> i.getArgument(0));

            EmergencyRequest spyRequest = spy(emergencyRequest);
            when(spyRequest.getProviderResponses()).thenReturn(List.of(providerResponse));
            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.of(spyRequest));

            when(providerResponseRepository.existsByEmergencyRequestIdAndProviderId(anyLong(), anyLong()))
                    .thenReturn(false);
            when(emergencyRequestRepository.save(any(EmergencyRequest.class))).thenAnswer(i -> i.getArgument(0));
            when(scheduler.deleteJob(any(JobKey.class))).thenReturn(true);

            emergencyRequestService.broadcastEmergencyRequest(emergencyRequest.getId());

            verify(emergencyRequestRepository).save(emergencyRequestCaptor.capture());
            EmergencyRequest saved = emergencyRequestCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(EmergencyRequestStatus.WAITING_ACCEPTANCE);
            verify(scheduler).deleteJob(jobKeyCaptor.capture());
            assertThat(jobKeyCaptor.getValue().getName()).contains("broadcast");
        }

        @Test
        @DisplayName("Set Status To NO_PROVIDERS When Radius Exceeds 50 And No Responses")
        void broadcastEmergencyRequestNoProvidersTest() throws SchedulerException
        {
            emergencyRequest.setSearchRadius(50);
            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.of(emergencyRequest));
            when(providerRepository.findProvidersWithinRadius(anyLong(), any(), anyDouble()))
                    .thenReturn(List.of());
            when(emergencyRequestRepository.save(any(EmergencyRequest.class))).thenAnswer(i -> i.getArgument(0));
            when(scheduler.deleteJob(any(JobKey.class))).thenReturn(true);

            emergencyRequestService.broadcastEmergencyRequest(emergencyRequest.getId());

            verify(emergencyRequestRepository).save(emergencyRequestCaptor.capture());
            EmergencyRequest saved = emergencyRequestCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(EmergencyRequestStatus.NO_PROVIDERS);
            verify(simpMessagingTemplate).convertAndSend(
                    eq("/topic/emergency-requests-no-providers-" + consumer.getId()),
                    nullable(EmergencyRequestResponse.class));
            verify(scheduler).deleteJob(jobKeyCaptor.capture());
        }

        @Test
        @DisplayName("Do Nothing If Status Is Not BROADCASTING And Delete Job")
        void broadcastEmergencyRequestNotBroadcastingTest() throws SchedulerException
        {
            emergencyRequest.setStatus(EmergencyRequestStatus.WAITING_ACCEPTANCE);
            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.of(emergencyRequest));

            emergencyRequestService.broadcastEmergencyRequest(emergencyRequest.getId());

            verify(scheduler).deleteJob(any(JobKey.class));
            verify(emergencyRequestRepository, never()).save(any());
            verify(providerResponseRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Get Pending Emergency Requests Tests")
    class GetPendingEmergencyRequestsTest
    {
        @Test
        @DisplayName("Successfully Get Pending Requests")
        void getPendingEmergencyRequestsSuccessTest()
        {
            when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
            when(providerResponseRepository.findByProviderIdAndEmergencyRequest_StatusInAndResponseTypeOrderByResponseTime(
                    provider.getId(),
                    List.of(EmergencyRequestStatus.BROADCASTING, EmergencyRequestStatus.WAITING_ACCEPTANCE),
                    ProviderResponseType.NO_RESPONSE))
                    .thenReturn(List.of(providerResponse));
            when(providerResponseMapper.toProviderResponseResponse(providerResponse))
                    .thenReturn(ProviderResponseResponse.builder().id(providerResponse.getId()).build());

            List<ProviderResponseResponse> responses = emergencyRequestService.getPendingEmergencyRequests(
                    provider.getId());

            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getId()).isEqualTo(providerResponse.getId());
        }
    }

    @Nested
    @DisplayName("Accept Emergency Request (Provider Accepts Request) Tests")
    class AcceptEmergencyRequestTest
    {
        @Test
        @DisplayName("Successfully Accept Emergency Request")
        void acceptEmergencyRequestSuccessTest()
        {
            ProviderResponseRequest request = ProviderResponseRequest.builder()
                    .providerResponseId(providerResponse.getId())
                    .location(LocationDTO.builder().latitude(30.0).longitude(31.0).build())
                    .proposedPrice(120.0)
                    .notes("On my way")
                    .build();

            GoogleMapsService.DistanceAndTime distanceAndTime = new GoogleMapsService.DistanceAndTime();
            distanceAndTime.setDistance(5.0);
            distanceAndTime.setEstimatedArrivalTime(30);

            when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
            when(providerResponseRepository.findById(providerResponse.getId())).thenReturn(Optional.of(providerResponse));
            when(googleMapsService.getDistanceAndTime(30.0, 31.0, 30.0, 31.0)).thenReturn(distanceAndTime);
            when(providerResponseRepository.save(any(ProviderResponse.class))).thenAnswer(i -> i.getArgument(0));
            when(providerResponseMapper.toProviderResponseResponse(any(ProviderResponse.class)))
                    .thenReturn(ProviderResponseResponse.builder().id(providerResponse.getId()).build());

            ProviderResponseResponse response = emergencyRequestService.acceptEmergencyRequest(
                    provider.getId(), request);

            assertThat(response).isNotNull();
            verify(providerResponseRepository).save(providerResponseCaptor.capture());
            ProviderResponse savedResponse = providerResponseCaptor.getValue();
            assertThat(savedResponse.getResponseType()).isEqualTo(ProviderResponseType.ACCEPTED_REQUEST);
            assertThat(savedResponse.getDistance()).isEqualTo(5.0);
            assertThat(savedResponse.getEstimatedArrivalTime()).isEqualTo(30);
            assertThat(savedResponse.getProposedPrice()).isEqualTo(120.0);
            assertThat(savedResponse.getNotes()).isEqualTo("On my way");
            assertThat(savedResponse.getResponseTime()).isNotNull();

            verify(notificationFactory).send(eq(consumer.getId()), eq(NotificationType.EMERGENCY_ALERT), anyMap());
            verify(simpMessagingTemplate).convertAndSend(
                    eq("/topic/emergency-requests-accepted-" + consumer.getId()),
                    any(ProviderResponseResponse.class));
        }

        @Test
        @DisplayName("Throw BadRequestException When Distance Is Too Far (>100 km)")
        void acceptEmergencyRequestDistanceTooFarTest()
        {
            ProviderResponseRequest request = ProviderResponseRequest.builder()
                    .providerResponseId(providerResponse.getId())
                    .location(locationDTO)
                    .build();

            GoogleMapsService.DistanceAndTime distanceAndTime = new GoogleMapsService.DistanceAndTime();
            distanceAndTime.setDistance(150.0);
            distanceAndTime.setEstimatedArrivalTime(30);

            when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
            when(providerResponseRepository.findById(providerResponse.getId())).thenReturn(Optional.of(providerResponse));
            when(googleMapsService.getDistanceAndTime(30.0, 31.0, 30.0, 31.0)).thenReturn(distanceAndTime);

            assertThatThrownBy(() -> emergencyRequestService.acceptEmergencyRequest(provider.getId(), request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Distance is too far");
        }

        @Test
        @DisplayName("Throw BadRequestException When Estimated Arrival Time > 120 minutes")
        void acceptEmergencyRequestArrivalTooLongTest()
        {
            ProviderResponseRequest request = ProviderResponseRequest.builder()
                    .providerResponseId(providerResponse.getId())
                    .location(locationDTO)
                    .build();

            GoogleMapsService.DistanceAndTime distanceAndTime = new GoogleMapsService.DistanceAndTime();
            distanceAndTime.setDistance(50.0);
            distanceAndTime.setEstimatedArrivalTime(130);

            when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
            when(providerResponseRepository.findById(providerResponse.getId())).thenReturn(Optional.of(providerResponse));
            when(googleMapsService.getDistanceAndTime(30.0, 31.0, 30.0, 31.0)).thenReturn(distanceAndTime);

            assertThatThrownBy(() -> emergencyRequestService.acceptEmergencyRequest(provider.getId(), request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("Estimated arrival time is too long");
        }
    }

    @Nested
    @DisplayName("Decline Emergency Request (Provider Declines) Tests")
    class DeclineEmergencyRequestTest
    {
        @Test
        @DisplayName("Successfully Decline Emergency Request")
        void declineEmergencyRequestSuccessTest()
        {
            when(providerRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
            when(providerResponseRepository.findById(providerResponse.getId())).thenReturn(Optional.of(providerResponse));
            when(providerResponseRepository.save(any(ProviderResponse.class))).thenAnswer(i -> i.getArgument(0));
            when(providerResponseMapper.toProviderResponseResponse(any(ProviderResponse.class)))
                    .thenReturn(ProviderResponseResponse.builder().id(providerResponse.getId()).build());

            ProviderResponseResponse response = emergencyRequestService.declineEmergencyRequest(
                    provider.getId(), providerResponse.getId());

            assertThat(response).isNotNull();
            verify(providerResponseRepository).save(providerResponseCaptor.capture());
            ProviderResponse saved = providerResponseCaptor.getValue();
            assertThat(saved.getResponseType()).isEqualTo(ProviderResponseType.DECLINED_REQUEST);
            assertThat(saved.getResponseTime()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Accept Provider Response (Consumer Accepts Provider) Tests")
    class AcceptProviderResponseTest
    {
        @Test
        @DisplayName("Successfully Accept Provider Response")
        void acceptProviderResponseSuccessTest() throws SchedulerException
        {
            providerResponse.setResponseType(ProviderResponseType.ACCEPTED_REQUEST);
            providerResponse.setProposedPrice(120.0);
            emergencyRequest.setStatus(EmergencyRequestStatus.WAITING_ACCEPTANCE);

            when(consumerRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(providerResponseRepository.findById(providerResponse.getId())).thenReturn(Optional.of(providerResponse));
            when(emergencyRequestRepository.save(any(EmergencyRequest.class))).thenAnswer(i -> i.getArgument(0));
            when(providerResponseRepository.save(any(ProviderResponse.class))).thenAnswer(i -> i.getArgument(0));
            when(providerRepository.save(any(Provider.class))).thenAnswer(i -> i.getArgument(0));
            when(consumerRepository.save(any(Consumer.class))).thenAnswer(i -> i.getArgument(0));
            when(providerResponseMapper.toProviderResponseResponse(any(ProviderResponse.class)))
                    .thenReturn(ProviderResponseResponse.builder().id(providerResponse.getId()).build());

            ProviderResponseResponse response = emergencyRequestService.acceptProviderResponse(
                    consumer.getId(), providerResponse.getId());

            assertThat(response).isNotNull();
            verify(emergencyRequestRepository).save(emergencyRequestCaptor.capture());
            EmergencyRequest savedRequest = emergencyRequestCaptor.getValue();
            assertThat(savedRequest.getStatus()).isEqualTo(EmergencyRequestStatus.ACCEPTED);
            assertThat(savedRequest.getPrice()).isEqualTo(120.0);
            assertThat(savedRequest.getSelectedProvider()).isEqualTo(provider);

            verify(providerResponseRepository).save(providerResponseCaptor.capture());
            ProviderResponse savedResponse = providerResponseCaptor.getValue();
            assertThat(savedResponse.getSelected()).isTrue();

            verify(providerRepository).save(providerCaptor.capture());
            Provider savedProvider = providerCaptor.getValue();
            assertThat(savedProvider.getTotalBookings()).isEqualTo(1);

            verify(consumerRepository).save(consumerCaptor.capture());
            Consumer savedConsumer = consumerCaptor.getValue();
            assertThat(savedConsumer.getTotalBookings()).isEqualTo(1);

            verify(scheduler).deleteJob(jobKeyCaptor.capture());
            assertThat(jobKeyCaptor.getValue().getName()).contains("broadcast");
            verify(notificationFactory).send(eq(provider.getId()), eq(NotificationType.PROVIDER_SELECTED), anyMap());
        }
    }

    @Nested
    @DisplayName("Decline Provider Response (Consumer Declines Provider) Tests")
    class DeclineProviderResponseTest
    {
        @Test
        @DisplayName("Successfully Decline Provider Response")
        void declineProviderResponseSuccessTest()
        {
            providerResponse.setResponseType(ProviderResponseType.ACCEPTED_REQUEST);
            providerResponse.setSelected(false);
            emergencyRequest.setStatus(EmergencyRequestStatus.WAITING_ACCEPTANCE);

            when(consumerRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(providerResponseRepository.findById(providerResponse.getId())).thenReturn(Optional.of(providerResponse));
            when(providerResponseRepository.save(any(ProviderResponse.class))).thenAnswer(i -> i.getArgument(0));
            when(providerResponseMapper.toProviderResponseResponse(any(ProviderResponse.class)))
                    .thenReturn(ProviderResponseResponse.builder().id(providerResponse.getId()).build());

            ProviderResponseResponse response = emergencyRequestService.declineProviderResponse(
                    consumer.getId(), providerResponse.getId());

            assertThat(response).isNotNull();
            verify(providerResponseRepository).save(providerResponseCaptor.capture());
            ProviderResponse saved = providerResponseCaptor.getValue();
            assertThat(saved.getResponseType()).isEqualTo(ProviderResponseType.NO_RESPONSE);
            assertThat(saved.getResponseTime()).isNotNull();

            verify(notificationFactory).send(eq(provider.getId()), eq(NotificationType.EMERGENCY_ALERT), anyMap());
        }
    }

    @Nested
    @DisplayName("Update Emergency Request Price Tests")
    class UpdateEmergencyRequestPriceTest
    {
        @Test
        @DisplayName("Successfully Update Price")
        void updateEmergencyRequestPriceSuccessTest()
        {
            UpdateEmergencyRequestPriceRequest request = UpdateEmergencyRequestPriceRequest.builder()
                    .emergencyRequestId(emergencyRequest.getId())
                    .updatedPrice(200.0)
                    .build();

            ProviderResponse declinedResponse = ProviderResponse.builder()
                    .id(21L)
                    .provider(provider)
                    .emergencyRequest(emergencyRequest)
                    .responseType(ProviderResponseType.DECLINED_REQUEST)
                    .build();

            when(consumerRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.of(emergencyRequest));
            when(emergencyRequestRepository.save(any(EmergencyRequest.class))).thenAnswer(i -> i.getArgument(0));
            when(providerResponseRepository.findByEmergencyRequestIdAndResponseType(
                    emergencyRequest.getId(), ProviderResponseType.DECLINED_REQUEST))
                    .thenReturn(List.of(declinedResponse));
            when(providerResponseRepository.save(any(ProviderResponse.class))).thenAnswer(i -> i.getArgument(0));
            when(emergencyRequestMapper.toEmergencyRequestResponse(any(EmergencyRequest.class)))
                    .thenReturn(EmergencyRequestResponse.builder().id(emergencyRequest.getId()).build());

            EmergencyRequestResponse response = emergencyRequestService.updateEmergencyRequestPrice(
                    consumer.getId(), request);

            assertThat(response).isNotNull();
            verify(emergencyRequestRepository).save(emergencyRequestCaptor.capture());
            EmergencyRequest saved = emergencyRequestCaptor.getValue();
            assertThat(saved.getPrice()).isEqualTo(200.0);

            verify(providerResponseRepository).save(providerResponseCaptor.capture());
            ProviderResponse savedResponse = providerResponseCaptor.getValue();
            assertThat(savedResponse.getResponseType()).isEqualTo(ProviderResponseType.NO_RESPONSE);

            verify(notificationFactory).send(eq(provider.getId()), eq(NotificationType.EMERGENCY_ALERT), anyMap());
        }
    }

    @Nested
    @DisplayName("Complete Emergency Request Tests")
    class CompleteEmergencyRequestTest
    {
        @Test
        @DisplayName("Successfully Complete Emergency Request")
        void completeEmergencyRequestSuccessTest()
        {
            emergencyRequest.setStatus(EmergencyRequestStatus.ACCEPTED);
            emergencyRequest.setSelectedProvider(provider);

            when(consumerRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.of(emergencyRequest));
            when(emergencyRequestRepository.save(any(EmergencyRequest.class))).thenAnswer(i -> i.getArgument(0));
            when(providerRepository.save(any(Provider.class))).thenAnswer(i -> i.getArgument(0));
            when(emergencyRequestMapper.toEmergencyRequestResponse(any(EmergencyRequest.class)))
                    .thenReturn(EmergencyRequestResponse.builder().id(emergencyRequest.getId()).build());

            EmergencyRequestResponse response = emergencyRequestService.completeEmergencyRequest(
                    consumer.getId(), emergencyRequest.getId());

            assertThat(response).isNotNull();
            verify(emergencyRequestRepository).save(emergencyRequestCaptor.capture());
            EmergencyRequest saved = emergencyRequestCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(EmergencyRequestStatus.COMPLETED);
            assertThat(saved.getCompletedAt()).isNotNull();

            verify(providerRepository).save(providerCaptor.capture());
            Provider savedProvider = providerCaptor.getValue();
            assertThat(savedProvider.getCompletedJobs()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Cancel Emergency Request Tests")
    class CancelEmergencyRequestTest
    {
        @Test
        @DisplayName("Successfully Cancel Emergency Request")
        void cancelEmergencyRequestSuccessTest() throws SchedulerException
        {
            emergencyRequest.setStatus(EmergencyRequestStatus.BROADCASTING);

            when(consumerRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(emergencyRequestRepository.findById(emergencyRequest.getId())).thenReturn(Optional.of(emergencyRequest));
            when(emergencyRequestRepository.save(any(EmergencyRequest.class))).thenAnswer(i -> i.getArgument(0));
            when(providerResponseRepository.findByEmergencyRequestId(emergencyRequest.getId()))
                    .thenReturn(List.of(providerResponse));
            when(emergencyRequestMapper.toEmergencyRequestResponse(any(EmergencyRequest.class)))
                    .thenReturn(EmergencyRequestResponse.builder().id(emergencyRequest.getId()).build());

            EmergencyRequestResponse response = emergencyRequestService.cancelEmergencyRequest(
                    consumer.getId(), emergencyRequest.getId());

            assertThat(response).isNotNull();
            verify(emergencyRequestRepository).save(emergencyRequestCaptor.capture());
            EmergencyRequest saved = emergencyRequestCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(EmergencyRequestStatus.CANCELLED);

            verify(notificationFactory).send(eq(provider.getId()), eq(NotificationType.EMERGENCY_ALERT), anyMap());
            verify(scheduler).deleteJob(jobKeyCaptor.capture());
            assertThat(jobKeyCaptor.getValue().getName()).contains("broadcast");
        }
    }

    @Nested
    @DisplayName("Get Accepted Emergency Requests Tests")
    class GetAcceptedEmergencyRequestsTest
    {
        @Test
        @DisplayName("Get Accepted Requests As Consumer")
        void getAcceptedEmergencyRequestsAsConsumerTest()
        {
            when(userRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(emergencyRequestRepository.findByConsumerIdAndStatusInAndSelectedProviderNotNullOrderByCreatedAtDesc(
                    consumer.getId(), List.of(EmergencyRequestStatus.ACCEPTED)))
                    .thenReturn(List.of(emergencyRequest));
            when(emergencyRequestMapper.toEmergencyRequestResponse(emergencyRequest))
                    .thenReturn(EmergencyRequestResponse.builder().id(emergencyRequest.getId()).build());

            List<EmergencyRequestResponse> responses = emergencyRequestService.getAcceptedEmergencyRequests(
                    consumer.getId());

            assertThat(responses).hasSize(1);
            assertThat(responses.get(0).getId()).isEqualTo(emergencyRequest.getId());
        }

        @Test
        @DisplayName("Get Accepted Requests As Provider")
        void getAcceptedEmergencyRequestsAsProviderTest()
        {
            when(userRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
            when(emergencyRequestRepository.findBySelectedProviderIdAndStatusInOrderByCreatedAtDesc(
                    provider.getId(), List.of(EmergencyRequestStatus.ACCEPTED)))
                    .thenReturn(List.of(emergencyRequest));
            when(emergencyRequestMapper.toEmergencyRequestResponse(emergencyRequest))
                    .thenReturn(EmergencyRequestResponse.builder().id(emergencyRequest.getId()).build());

            List<EmergencyRequestResponse> responses = emergencyRequestService.getAcceptedEmergencyRequests(
                    provider.getId());

            assertThat(responses).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Get Emergency Requests History Tests")
    class GetEmergencyRequestsHistoryTest
    {

        @Test
        @DisplayName("Get History As Consumer")
        void getEmergencyRequestsHistoryAsConsumerTest()
        {
            emergencyRequest.setStatus(EmergencyRequestStatus.COMPLETED);
            when(userRepository.findById(consumer.getId())).thenReturn(Optional.of(consumer));
            when(emergencyRequestRepository.findByConsumerIdAndStatusInAndSelectedProviderNotNullOrderByCreatedAtDesc(
                    consumer.getId(), List.of(EmergencyRequestStatus.COMPLETED, EmergencyRequestStatus.EXPIRED)))
                    .thenReturn(List.of(emergencyRequest));
            when(emergencyRequestMapper.toEmergencyRequestResponse(emergencyRequest))
                    .thenReturn(EmergencyRequestResponse.builder().id(emergencyRequest.getId()).build());

            List<EmergencyRequestResponse> responses = emergencyRequestService.getEmergencyRequestsHistory(
                    consumer.getId());

            assertThat(responses).hasSize(1);
        }

        @Test
        @DisplayName("Get History As Provider")
        void getEmergencyRequestsHistoryAsProviderTest()
        {
            emergencyRequest.setStatus(EmergencyRequestStatus.COMPLETED);
            emergencyRequest.setSelectedProvider(provider);
            when(userRepository.findById(provider.getId())).thenReturn(Optional.of(provider));
            when(emergencyRequestRepository.findBySelectedProviderIdAndStatusInOrderByCreatedAtDesc(
                    provider.getId(), List.of(EmergencyRequestStatus.COMPLETED, EmergencyRequestStatus.EXPIRED)))
                    .thenReturn(List.of(emergencyRequest));
            when(emergencyRequestMapper.toEmergencyRequestResponse(emergencyRequest))
                    .thenReturn(EmergencyRequestResponse.builder().id(emergencyRequest.getId()).build());

            List<EmergencyRequestResponse> responses = emergencyRequestService.getEmergencyRequestsHistory(
                    provider.getId());

            assertThat(responses).hasSize(1);
        }
    }
}
