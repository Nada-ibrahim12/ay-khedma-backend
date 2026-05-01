package com.aykhedma.service;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.request.EmergencyRequestRequest;
import com.aykhedma.dto.request.ProviderResponseRequest;
import com.aykhedma.dto.request.UpdateEmergencyRequestPriceRequest;
import com.aykhedma.dto.response.EmergencyRequestResponse;
import com.aykhedma.dto.response.ProviderResponseResponse;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.exception.ForbiddenException;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.job.BroadcastEmergencyRequestJob;
import com.aykhedma.mapper.EmergencyRequestMapper;
import com.aykhedma.mapper.ProviderResponseMapper;
import com.aykhedma.model.emergency.EmergencyRequest;
import com.aykhedma.model.emergency.EmergencyRequestStatus;
import com.aykhedma.model.emergency.ProviderResponse;
import com.aykhedma.model.emergency.ProviderResponseType;
import com.aykhedma.model.location.Location;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.user.User;
import com.aykhedma.model.user.UserType;
import com.aykhedma.repository.*;
import lombok.RequiredArgsConstructor;
import org.quartz.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class EmergencyRequestServiceImpl implements EmergencyRequestService
{
    private final EmergencyRequestRepository emergencyRequestRepository;
    private final ProviderResponseRepository providerResponseRepository;
    private final UserRepository userRepository;
    private final ConsumerRepository consumerRepository;
    private final ProviderRepository providerRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final LocationRepository locationRepository;
    private final EmergencyRequestMapper emergencyRequestMapper;
    private final ProviderResponseMapper providerResponseMapper;
    private final Scheduler scheduler;
    private final GoogleMapsService googleMapsService;
    private final RestTemplate restTemplate;
    private final SimpMessagingTemplate simpMessagingTemplate;


    @Override
    @Transactional
    public EmergencyRequestResponse requestEmergencyRequest (Long consumerId, EmergencyRequestRequest request)
    {
        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found"));

        ServiceType serviceType = serviceTypeRepository.findById(request.getServiceTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Service type not found"));

        LocationDTO locationDTO = request.getLocation();
        Location location = Location.builder()
                .latitude(locationDTO.getLatitude())
                .longitude(locationDTO.getLongitude())
                .address(locationDTO.getAddress())
                .area(locationDTO.getArea())
                .city(locationDTO.getCity())
                .build();
        locationRepository.save(location);

        EmergencyRequest emergencyRequest = EmergencyRequest.builder()
                .consumer(consumer)
                .serviceType(serviceType)
                .location(location)
                .price(request.getPrice())
                .description(request.getDescription())
                .searchRadius(5)
                .status(EmergencyRequestStatus.BROADCASTING)
                .build();
        emergencyRequestRepository.save(emergencyRequest);

        JobDetail jobDetail = JobBuilder.newJob(BroadcastEmergencyRequestJob.class)
                .withIdentity("broadcast_" + emergencyRequest.getId())
                .usingJobData("requestId", emergencyRequest.getId())
                .build();

        Trigger trigger = TriggerBuilder.newTrigger().startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInMinutes(1).repeatForever())
                .build();
        try
        {
            scheduler.scheduleJob(jobDetail, trigger);
        }
        catch (SchedulerException e)
        {
            throw new RuntimeException(e);
        }

        return emergencyRequestMapper.toEmergencyRequestResponse(emergencyRequest);
    }

    @Override
    @Transactional
    public void broadcastEmergencyRequest (Long requestId)
    {
        EmergencyRequest emergencyRequest = emergencyRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Emergency request not found"));

        if (emergencyRequest.getStatus() != EmergencyRequestStatus.BROADCASTING)
        {
            try
            {
                scheduler.deleteJob(new JobKey("broadcast_" + requestId));
            }
            catch (SchedulerException e)
            {
                throw new RuntimeException(e);
            }
            return;
        }

        ServiceType serviceType = emergencyRequest.getServiceType();
        if (serviceType == null)
            throw new ResourceNotFoundException("Emergency request's service type not found");

        Location location = emergencyRequest.getLocation();
        if (location == null)
            throw new ResourceNotFoundException("Emergency request's location not found");

        Integer searchRadius = emergencyRequest.getSearchRadius();
        if (searchRadius == null)
            throw new ResourceNotFoundException("Emergency request's search radius not found");

        List<Provider> providers = providerRepository.findProvidersWithinRadius
                (serviceType.getId(), location.getCoordinates(), searchRadius * 1000);

        for (Provider provider : providers)
        {
            if (providerResponseRepository.existsByEmergencyRequestIdAndProviderId(requestId, provider.getId()))
                continue;

            ProviderResponse providerResponse = ProviderResponse.builder()
                    .provider(provider)
                    .emergencyRequest(emergencyRequest)
                    .responseType(ProviderResponseType.NO_RESPONSE)
                    .build();
            providerResponse.estimateArrivalTime();
            providerResponseRepository.save(providerResponse);

            // Notify the provider that he has an emergency request
        }

        int newSearchRadius = searchRadius + 5;
        if (newSearchRadius > 50)
        {
            if (emergencyRequest.getProviderResponses().isEmpty())
            {
                emergencyRequest.setStatus(EmergencyRequestStatus.NO_PROVIDERS);
                emergencyRequestRepository.save(emergencyRequest);

                simpMessagingTemplate.convertAndSendToUser(
                        emergencyRequest.getConsumer().getId().toString(),
                        "/queue/emergency-requests/no-providers",
                        emergencyRequestMapper.toEmergencyRequestResponse(emergencyRequest));
            }
            else
            {
                emergencyRequest.setStatus(EmergencyRequestStatus.WAITING_ACCEPTANCE);
                emergencyRequestRepository.save(emergencyRequest);
            }

            try
            {
                scheduler.deleteJob(new JobKey("broadcast_" + requestId));
            }
            catch (SchedulerException e)
            {
                throw new RuntimeException(e);
            }

        }
        else
        {
            emergencyRequest.setSearchRadius(newSearchRadius);
        }
    }

    @Override
    @Transactional
    public List<ProviderResponseResponse> getPendingEmergencyRequests (Long providerId)
    {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        List<ProviderResponse> providerResponses = providerResponseRepository
                .findByProviderIdAndEmergencyRequest_StatusInAndResponseTypeOrderByResponseTime(
                        providerId,
                        List.of(EmergencyRequestStatus.BROADCASTING, EmergencyRequestStatus.WAITING_ACCEPTANCE),
                        ProviderResponseType.NO_RESPONSE);

        return providerResponses.stream().map(providerResponseMapper::toProviderResponseResponse).toList();
    }

    @Override
    @Transactional
    public ProviderResponseResponse acceptEmergencyRequest (Long providerId, ProviderResponseRequest request)
    {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        ProviderResponse providerResponse = providerResponseRepository.findById(request.getProviderResponseId())
                .orElseThrow(() -> new ResourceNotFoundException("Provider response not found"));

        if (!providerId.equals(providerResponse.getProvider().getId()))
            throw new ForbiddenException("Provider response does not belong to this provider");

        if (providerResponse.getResponseType() != ProviderResponseType.NO_RESPONSE)
            throw new BadRequestException("Cannot accept. Provider already " + providerResponse.getResponseType());

        EmergencyRequest emergencyRequest = providerResponse.getEmergencyRequest();
        if (emergencyRequest == null)
            throw new ResourceNotFoundException("Emergency request not found");

        if (emergencyRequest.getStatus() != EmergencyRequestStatus.BROADCASTING && emergencyRequest.getStatus() != EmergencyRequestStatus.WAITING_ACCEPTANCE)
            throw new BadRequestException("Emergency request cannot be accepted, it has already been " + emergencyRequest.getStatus());

        Location distLocation = emergencyRequest.getLocation();
        if (distLocation == null)
            throw new ResourceNotFoundException("Emergency request's location not found");

        Double distLat = distLocation.getLatitude(), distLong = distLocation.getLongitude();
        Double originLat = request.getLocation().getLatitude(), originLong = request.getLocation().getLongitude();
        GoogleMapsService.DistanceAndTime distanceAndTime = googleMapsService.getDistanceAndTime(originLat, originLong, distLat, distLong);

        double distance = distanceAndTime.getDistance();
        int estimatedArrivalTime = distanceAndTime.getEstimatedArrivalTime();

        if (distance > 100)
            throw new BadRequestException("Distance is too far");
        else if (estimatedArrivalTime > 120)
            throw new BadRequestException("Estimated arrival time is too long");

        providerResponse.setDistance(distance);
        providerResponse.setEstimatedArrivalTime(estimatedArrivalTime);
        providerResponse.setProposedPrice(request.getProposedPrice());
        providerResponse.setNotes(request.getNotes());
        providerResponse.setResponseType(ProviderResponseType.ACCEPTED_REQUEST);
        providerResponse.setResponseTime(LocalDateTime.now());
        providerResponseRepository.save(providerResponse);

        // Notify the consumer that a provider accepted his request

        simpMessagingTemplate.convertAndSendToUser(
                emergencyRequest.getConsumer().getId().toString(),
                "/queue/emergency-requests/accepted-provider-responses",
                providerResponseMapper.toProviderResponseResponse(providerResponse));

        return providerResponseMapper.toProviderResponseResponse(providerResponse);
    }

    @Override
    @Transactional
    public ProviderResponseResponse declineEmergencyRequest (Long providerId, Long providerResponseId)
    {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));

        ProviderResponse providerResponse = providerResponseRepository.findById(providerResponseId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider response not found"));

        if (!providerId.equals(providerResponse.getProvider().getId()))
            throw new ForbiddenException("Provider response does not belong to this provider");

        if (providerResponse.getResponseType() != ProviderResponseType.NO_RESPONSE)
            throw new BadRequestException("Cannot decline. Provider already " + providerResponse.getResponseType());

        EmergencyRequest emergencyRequest = providerResponse.getEmergencyRequest();
        if (emergencyRequest == null)
            throw new ResourceNotFoundException("Emergency request not found");

        if (emergencyRequest.getStatus() != EmergencyRequestStatus.BROADCASTING && emergencyRequest.getStatus() != EmergencyRequestStatus.WAITING_ACCEPTANCE)
            throw new BadRequestException("Emergency request cannot be accepted, it has already been " + emergencyRequest.getStatus());

        providerResponse.setResponseType(ProviderResponseType.DECLINED_REQUEST);
        providerResponse.setResponseTime(LocalDateTime.now());
        providerResponseRepository.save(providerResponse);

        return providerResponseMapper.toProviderResponseResponse(providerResponse);
    }

    @Override
    @Transactional
    public ProviderResponseResponse acceptProviderResponse (Long consumerId, Long providerResponseId)
    {
        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found"));

        ProviderResponse providerResponse = providerResponseRepository.findById(providerResponseId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider response not found"));

        EmergencyRequest emergencyRequest = providerResponse.getEmergencyRequest();
        if (emergencyRequest == null)
            throw new ResourceNotFoundException("Emergency request not found");

        if (!consumerId.equals(emergencyRequest.getConsumer().getId()))
            throw new ForbiddenException("Provider response does not belong to an emergency request that belongs to this consumer");

        if (emergencyRequest.getStatus() != EmergencyRequestStatus.BROADCASTING && emergencyRequest.getStatus() != EmergencyRequestStatus.WAITING_ACCEPTANCE)
            throw new BadRequestException("Provider response cannot be accepted, the emergency request has already been " + emergencyRequest.getStatus());

        if (providerResponse.getResponseType() != ProviderResponseType.ACCEPTED_REQUEST)
            throw new BadRequestException("Provider response cannot be accepted, it has already been " + providerResponse.getResponseType());

        emergencyRequest.setPrice(providerResponse.getProposedPrice());
        emergencyRequest.setStatus(EmergencyRequestStatus.ACCEPTED);
        emergencyRequest.setSelectedProvider(providerResponse.getProvider());
        emergencyRequestRepository.save(emergencyRequest);

        providerResponse.setSelected(true);
        providerResponseRepository.save(providerResponse);

        try
        {
            scheduler.deleteJob(new JobKey("broadcast_" + emergencyRequest.getId()));
        }
        catch (SchedulerException e)
        {
            throw new RuntimeException(e);
        }

        // Notify the provider that the consumer accepted his response

        return providerResponseMapper.toProviderResponseResponse(providerResponse);
    }

    @Override
    @Transactional
    public ProviderResponseResponse declineProviderResponse (Long consumerId, Long providerResponseId)
    {
        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found"));

        ProviderResponse providerResponse = providerResponseRepository.findById(providerResponseId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider response not found"));

        EmergencyRequest emergencyRequest = providerResponse.getEmergencyRequest();
        if (emergencyRequest == null)
            throw new ResourceNotFoundException("Emergency request not found");

        if (!consumerId.equals(emergencyRequest.getConsumer().getId()))
            throw new ForbiddenException("Provider response does not belong to an emergency request that belongs to this consumer");

        if (providerResponse.getResponseType() != ProviderResponseType.ACCEPTED_REQUEST)
            throw new BadRequestException("Provider response cannot be declined, it has already been " + providerResponse.getResponseType());

        if (providerResponse.getSelected() && emergencyRequest.getAcceptedProviders() == providerResponse.getProvider())
            throw new BadRequestException("Provider response cannot be declined, it has already been selected");

        providerResponse.setResponseType(ProviderResponseType.NO_RESPONSE);
        providerResponse.setResponseTime(LocalDateTime.now());
        providerResponseRepository.save(providerResponse);

        // Notify the provider that the consumer declined his response and he can submit another response if he wants

        return providerResponseMapper.toProviderResponseResponse(providerResponse);
    }

    @Override
    @Transactional
    public EmergencyRequestResponse updateEmergencyRequestPrice (Long consumerId, UpdateEmergencyRequestPriceRequest request)
    {
        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found"));

        EmergencyRequest emergencyRequest = emergencyRequestRepository.findById(request.getEmergencyRequestId())
                .orElseThrow(() -> new ResourceNotFoundException("Emergency request not found"));

        if (!consumerId.equals(emergencyRequest.getConsumer().getId()))
            throw new ForbiddenException("Emergency request does not belong to this consumer");

        if (emergencyRequest.getStatus() != EmergencyRequestStatus.BROADCASTING && emergencyRequest.getStatus() != EmergencyRequestStatus.WAITING_ACCEPTANCE)
            throw new BadRequestException("Emergency request cannot be updated, it has already been " + emergencyRequest.getStatus());

        emergencyRequest.setPrice(request.getUpdatedPrice());
        emergencyRequestRepository.save(emergencyRequest);

        List<ProviderResponse> declinedProviderResponses =
                providerResponseRepository.findByEmergencyRequestIdAndResponseType(emergencyRequest.getId(), ProviderResponseType.DECLINED_REQUEST);

        for (ProviderResponse providerResponse : declinedProviderResponses)
        {
            providerResponse.setResponseType(ProviderResponseType.NO_RESPONSE);
            providerResponseRepository.save(providerResponse);

            // Notify the provider that the emergency request's price got updated
        }

        return emergencyRequestMapper.toEmergencyRequestResponse(emergencyRequest);
    }

    @Override
    @Transactional
    public EmergencyRequestResponse cancelEmergencyRequest (Long consumerId, Long emergencyRequestId)
    {
        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found"));

        EmergencyRequest emergencyRequest = emergencyRequestRepository.findById(emergencyRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Emergency request not found"));

        if (!consumerId.equals(emergencyRequest.getConsumer().getId()))
            throw new ForbiddenException("Emergency request does not belong to this consumer");

        if ( emergencyRequest.getStatus() == EmergencyRequestStatus.ACCEPTED || emergencyRequest.getStatus() == EmergencyRequestStatus.CANCELLED)
            throw new BadRequestException("Emergency request cannot be cancelled, it has already been " + emergencyRequest.getStatus());

        emergencyRequest.setStatus(EmergencyRequestStatus.CANCELLED);
        emergencyRequestRepository.save(emergencyRequest);

        try
        {
            scheduler.deleteJob(new JobKey("broadcast_" + emergencyRequest.getId()));
        }
        catch (SchedulerException e)
        {
            throw new RuntimeException(e);
        }

        return emergencyRequestMapper.toEmergencyRequestResponse(emergencyRequest);
    }

    @Override
    public List<EmergencyRequestResponse> getEmergencyRequestsHistory (Long userId)
    {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<EmergencyRequest>  emergencyRequests;
        if (user.getRole() == UserType.CONSUMER)
        {
            emergencyRequests = emergencyRequestRepository
                    .findByConsumerIdAndStatusOrderByCreatedAtDesc(userId, EmergencyRequestStatus.ACCEPTED);
        }
        else if (user.getRole() == UserType.PROVIDER)
        {
            emergencyRequests = emergencyRequestRepository
                    .findBySelectedProviderIdAndStatusOrderByCreatedAtDesc(userId, EmergencyRequestStatus.ACCEPTED);
        }
        else
            throw new ForbiddenException("User is not a provider or a consumer");

        return emergencyRequests.stream().map(emergencyRequestMapper::toEmergencyRequestResponse).toList();
    }
}
