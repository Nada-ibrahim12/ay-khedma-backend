package com.aykhedma.service;

import com.aykhedma.dto.request.*;
import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.response.EmergencyRequestResponse;
import com.aykhedma.dto.response.PriceRecommendationResponse;
import com.aykhedma.dto.response.ProviderResponseResponse;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.exception.ForbiddenException;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.job.BroadcastEmergencyRequestJob;
import com.aykhedma.mapper.EmergencyRequestMapper;
import com.aykhedma.mapper.ProviderResponseMapper;
import com.aykhedma.model.notification.NotificationType;
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
import java.util.Map;

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
    private final NotificationFactory notificationFactory;
    private final BookingRepository bookingRepository;

    @Override
    public EmergencyRequestResponse getCurrentEmergencyRequest(Long consumerId)
    {
        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found"));

        EmergencyRequest currentEmergencyRequest = emergencyRequestRepository
                .findTopByConsumerIdAndStatusInOrderByCreatedAtDesc(consumerId,
                        List.of(EmergencyRequestStatus.BROADCASTING, EmergencyRequestStatus.WAITING_ACCEPTANCE));

        if (currentEmergencyRequest == null)
            throw new ResourceNotFoundException("No currently ongoing emergency request found for this consumer");

        JobKey jobKey = new JobKey("broadcast_" + currentEmergencyRequest.getId());
        boolean jobExists;
        try
        {
            jobExists = scheduler.checkExists(jobKey);
        }
        catch (SchedulerException e)
        {
            throw new RuntimeException(e);
        }

        if (!jobExists && currentEmergencyRequest.getStatus() == EmergencyRequestStatus.BROADCASTING)
        {
            JobDetail jobDetail = JobBuilder.newJob(BroadcastEmergencyRequestJob.class)
                .withIdentity("broadcast_" + currentEmergencyRequest.getId())
                .usingJobData("requestId", currentEmergencyRequest.getId())
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
        }

        return emergencyRequestMapper.toEmergencyRequestResponse(currentEmergencyRequest);
    }

    @Override
    public PriceRecommendationResponse getEmergencyRequestPriceRecommendation (Long consumerId, PriceRecommendationRequest request)
    {
        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found"));

        ServiceType serviceType = serviceTypeRepository.findById(request.getServiceTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Service type not found"));

        LocationDTO locationDTO = request.getLocation();
        GoogleMapsService.LocationDetails locationDetails =
                googleMapsService.getLocationDetails(locationDTO.getLatitude(), locationDTO.getLongitude());

        String area = locationDetails.getArea();
        if (area == null)
            return PriceRecommendationResponse.builder().price(null).build();

        Double price = providerRepository.getAveragePrice(serviceType, area);
        if (price == null)
            return PriceRecommendationResponse.builder().price(null).build();
        else
            return PriceRecommendationResponse.builder().price(Math.ceil(price * 1.25)).build();
    }

    @Override
    @Transactional
    public EmergencyRequestResponse requestEmergencyRequest(Long consumerId, EmergencyRequestRequest request)
    {
        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found"));

        ServiceType serviceType = serviceTypeRepository.findById(request.getServiceTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Service type not found"));

        EmergencyRequest currentEmergencyRequest = emergencyRequestRepository
                .findTopByConsumerIdAndStatusInOrderByCreatedAtDesc(consumerId,
                        List.of(EmergencyRequestStatus.BROADCASTING, EmergencyRequestStatus.WAITING_ACCEPTANCE));

        if (currentEmergencyRequest != null)
            throw new BadRequestException(
                    "Cannot request an emergency request. A currently ongoing emergency request for this consumer already exists");

        LocationDTO locationDTO = request.getLocation();
        GoogleMapsService.LocationDetails locationDetails =
                googleMapsService.getLocationDetails(locationDTO.getLatitude(), locationDTO.getLongitude());

        Location location = Location.builder()
                .latitude(locationDTO.getLatitude())
                .longitude(locationDTO.getLongitude())
                .address(locationDetails.getAddress())
                .area(locationDetails.getArea())
                .city(locationDetails.getCity())
                .country(locationDetails.getCountry())
                .addressAr(locationDetails.getAddressAr())
                .areaAr(locationDetails.getAreaAr())
                .cityAr(locationDetails.getCityAr())
                .countryAr(locationDetails.getCountryAr())
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
    public void broadcastEmergencyRequest(Long requestId)
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

        List<Provider> providers = providerRepository.findProvidersWithinRadius(serviceType.getId(),
                location.getCoordinates(), searchRadius * 1000);

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
            notificationFactory.send(provider.getId(),
                    NotificationType.EMERGENCY_OFFER,
                    Map.of(
                            "title", "New Emergency Request",
                            "message",
                            "A new " + serviceType.getName() + " emergency request is available in your area",
                            "emergencyRequestId", requestId.toString(),
                            "serviceType", serviceType.getName(),
                            "location", location.getAddress()));
        }

        int newSearchRadius = searchRadius + 5;
        if (newSearchRadius > 50)
        {
            if (emergencyRequest.getProviderResponses().isEmpty())
            {
                emergencyRequest.setStatus(EmergencyRequestStatus.NO_PROVIDERS);
                emergencyRequestRepository.save(emergencyRequest);

                simpMessagingTemplate.convertAndSend(
                        "/topic/emergency-requests-no-providers-" + emergencyRequest.getConsumer().getId(),
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
            emergencyRequestRepository.save(emergencyRequest);
        }
    }

    @Override
    @Transactional
    public List<ProviderResponseResponse> getPendingEmergencyRequests(Long providerId)
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
    public ProviderResponseResponse acceptEmergencyRequest(Long providerId, ProviderResponseRequest request)
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

        if (emergencyRequest.getStatus() != EmergencyRequestStatus.BROADCASTING
                && emergencyRequest.getStatus() != EmergencyRequestStatus.WAITING_ACCEPTANCE)
            throw new BadRequestException(
                    "Emergency request cannot be accepted, it has already been " + emergencyRequest.getStatus());

        Location distLocation = emergencyRequest.getLocation();
        if (distLocation == null)
            throw new ResourceNotFoundException("Emergency request's location not found");

        Double distLat = distLocation.getLatitude(), distLong = distLocation.getLongitude();
        Double originLat = request.getLocation().getLatitude(), originLong = request.getLocation().getLongitude();
        GoogleMapsService.DistanceAndTime distanceAndTime = googleMapsService.getDistanceAndTime(originLat, originLong,
                distLat, distLong);

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
        notificationFactory.send(emergencyRequest.getConsumer().getId(),
                NotificationType.EMERGENCY_ALERT,
                Map.of(
                        "title", "Provider Response",
                        "message", provider.getName() + " accepted your emergency request",
                        "emergencyRequestId", emergencyRequest.getId().toString(),
                        "providerId", provider.getId().toString(),
                        "estimatedArrivalTime", String.valueOf(estimatedArrivalTime),
                        "proposedPrice", request.getProposedPrice().toString()));

        simpMessagingTemplate.convertAndSend(
                "/topic/emergency-requests-accepted-" + emergencyRequest.getConsumer().getId(),
                providerResponseMapper.toProviderResponseResponse(providerResponse));

        return providerResponseMapper.toProviderResponseResponse(providerResponse);
    }

    @Override
    @Transactional
    public ProviderResponseResponse declineEmergencyRequest(Long providerId, Long providerResponseId)
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

        if (emergencyRequest.getStatus() != EmergencyRequestStatus.BROADCASTING
                && emergencyRequest.getStatus() != EmergencyRequestStatus.WAITING_ACCEPTANCE)
            throw new BadRequestException(
                    "Emergency request cannot be accepted, it has already been " + emergencyRequest.getStatus());

        providerResponse.setResponseType(ProviderResponseType.DECLINED_REQUEST);
        providerResponse.setResponseTime(LocalDateTime.now());
        providerResponseRepository.save(providerResponse);

        return providerResponseMapper.toProviderResponseResponse(providerResponse);
    }

    @Override
    @Transactional
    public ProviderResponseResponse acceptProviderResponse(Long consumerId, Long providerResponseId)
    {
        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found"));

        ProviderResponse providerResponse = providerResponseRepository.findById(providerResponseId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider response not found"));

        EmergencyRequest emergencyRequest = providerResponse.getEmergencyRequest();
        if (emergencyRequest == null)
            throw new ResourceNotFoundException("Emergency request not found");

        if (!consumerId.equals(emergencyRequest.getConsumer().getId()))
            throw new ForbiddenException(
                    "Provider response does not belong to an emergency request that belongs to this consumer");

        if (emergencyRequest.getStatus() != EmergencyRequestStatus.BROADCASTING
                && emergencyRequest.getStatus() != EmergencyRequestStatus.WAITING_ACCEPTANCE)
            throw new BadRequestException(
                    "Provider response cannot be accepted, the emergency request has already been "
                            + emergencyRequest.getStatus());

        if (providerResponse.getResponseType() != ProviderResponseType.ACCEPTED_REQUEST)
            throw new BadRequestException(
                    "Provider response cannot be accepted, provider already " + providerResponse.getResponseType());

        emergencyRequest.setPrice(providerResponse.getProposedPrice());
        emergencyRequest.setStatus(EmergencyRequestStatus.ACCEPTED);
        emergencyRequest.setSelectedProvider(providerResponse.getProvider());
        emergencyRequestRepository.save(emergencyRequest);

        providerResponse.setSelected(true);
        providerResponseRepository.save(providerResponse);

        Provider provider = providerResponse.getProvider();
        provider.setTotalBookings((provider.getTotalBookings() != null ? provider.getTotalBookings() : 0) + 1);
        updateProviderRates(provider);
        providerRepository.save(provider);

        consumer.setTotalBookings((consumer.getTotalBookings() != null ? consumer.getTotalBookings() : 0) + 1);
        updateConsumerRates(consumer);
        consumerRepository.save(consumer);

        try
        {
            scheduler.deleteJob(new JobKey("broadcast_" + emergencyRequest.getId()));
        }
        catch (SchedulerException e)
        {
            throw new RuntimeException(e);
        }

        // Notify the provider that the consumer accepted his response
        notificationFactory.send(providerResponse.getProvider().getId(),
                NotificationType.PROVIDER_SELECTED,
                Map.of(
                        "title", "Request Accepted",
                        "message", emergencyRequest.getConsumer().getName() + " accepted your emergency response",
                        "emergencyRequestId", emergencyRequest.getId().toString(),
                        "consumerPhone", emergencyRequest.getConsumer().getPhoneNumber()));

        return providerResponseMapper.toProviderResponseResponse(providerResponse);
    }

    @Override
    @Transactional
    public ProviderResponseResponse declineProviderResponse(Long consumerId, Long providerResponseId)
    {
        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found"));

        ProviderResponse providerResponse = providerResponseRepository.findById(providerResponseId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider response not found"));

        EmergencyRequest emergencyRequest = providerResponse.getEmergencyRequest();
        if (emergencyRequest == null)
            throw new ResourceNotFoundException("Emergency request not found");

        if (!consumerId.equals(emergencyRequest.getConsumer().getId()))
            throw new ForbiddenException(
                    "Provider response does not belong to an emergency request that belongs to this consumer");

        if (providerResponse.getResponseType() != ProviderResponseType.ACCEPTED_REQUEST)
            throw new BadRequestException(
                    "Provider response cannot be declined, provider already " + providerResponse.getResponseType());

        if (providerResponse.getSelected() && emergencyRequest.getAcceptedProviders() == providerResponse.getProvider())
            throw new BadRequestException("Provider response cannot be declined, it has already been selected");

        providerResponse.setResponseType(ProviderResponseType.NO_RESPONSE);
        providerResponse.setResponseTime(LocalDateTime.now());
        providerResponseRepository.save(providerResponse);

        // Notify the provider that the consumer declined his response and he can submit
        // another response if he wants
        notificationFactory.send(providerResponse.getProvider().getId(),
                NotificationType.EMERGENCY_ALERT,
                Map.of(
                        "title", "Response Declined",
                        "message",
                        "The consumer declined your emergency response. You can submit another response if interested.",
                        "emergencyRequestId", emergencyRequest.getId().toString()));

        return providerResponseMapper.toProviderResponseResponse(providerResponse);
    }

    @Override
    @Transactional
    public EmergencyRequestResponse updateEmergencyRequestPrice(Long consumerId, UpdateEmergencyRequestPriceRequest request)
    {
        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found"));

        EmergencyRequest emergencyRequest = emergencyRequestRepository.findById(request.getEmergencyRequestId())
                .orElseThrow(() -> new ResourceNotFoundException("Emergency request not found"));

        if (!consumerId.equals(emergencyRequest.getConsumer().getId()))
            throw new ForbiddenException("Emergency request does not belong to this consumer");

        if (emergencyRequest.getStatus() != EmergencyRequestStatus.BROADCASTING
                && emergencyRequest.getStatus() != EmergencyRequestStatus.WAITING_ACCEPTANCE)
            throw new BadRequestException(
                    "Emergency request cannot be updated, it has already been " + emergencyRequest.getStatus());

        emergencyRequest.setPrice(request.getUpdatedPrice());
        emergencyRequestRepository.save(emergencyRequest);

        List<ProviderResponse> declinedProviderResponses = providerResponseRepository
                .findByEmergencyRequestIdAndResponseType(emergencyRequest.getId(),
                        ProviderResponseType.DECLINED_REQUEST);

        for (ProviderResponse providerResponse : declinedProviderResponses)
        {
            providerResponse.setResponseType(ProviderResponseType.NO_RESPONSE);
            providerResponseRepository.save(providerResponse);

            // Notify the provider that the emergency request's price got updated
            notificationFactory.send(providerResponse.getProvider().getId(),
                    NotificationType.EMERGENCY_ALERT,
                    Map.of(
                            "title", "Price Updated",
                            "message", "The emergency request price has been updated to " + request.getUpdatedPrice(),
                            "emergencyRequestId", emergencyRequest.getId().toString(),
                            "newPrice", String.valueOf(request.getUpdatedPrice())));
        }

        return emergencyRequestMapper.toEmergencyRequestResponse(emergencyRequest);
    }

    @Override
    @Transactional
    public EmergencyRequestResponse completeEmergencyRequest (Long consumerId, Long emergencyRequestId)
    {
        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found"));

        EmergencyRequest emergencyRequest = emergencyRequestRepository.findById(emergencyRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Emergency request not found"));

        if (!consumerId.equals(emergencyRequest.getConsumer().getId()))
            throw new ForbiddenException("Emergency request does not belong to this consumer");

        if (emergencyRequest.getStatus() != EmergencyRequestStatus.ACCEPTED)
            throw new BadRequestException("Emergency request cannot be completed, it has already been " + emergencyRequest.getStatus());

        emergencyRequest.setStatus(EmergencyRequestStatus.COMPLETED);
        emergencyRequest.setCompletedAt(LocalDateTime.now());
        emergencyRequestRepository.save(emergencyRequest);

        Provider provider = emergencyRequest.getSelectedProvider();
        provider.setCompletedJobs((provider.getCompletedJobs() != null ? provider.getCompletedJobs() : 0) + 1);
        updateProviderRates(provider);
        providerRepository.save(provider);

        return emergencyRequestMapper.toEmergencyRequestResponse(emergencyRequest);
    }

    @Override
    @Transactional
    public EmergencyRequestResponse cancelEmergencyRequest(Long consumerId, Long emergencyRequestId)
    {
        Consumer consumer = consumerRepository.findById(consumerId)
                .orElseThrow(() -> new ResourceNotFoundException("Consumer not found"));

        EmergencyRequest emergencyRequest = emergencyRequestRepository.findById(emergencyRequestId)
                .orElseThrow(() -> new ResourceNotFoundException("Emergency request not found"));

        if (!consumerId.equals(emergencyRequest.getConsumer().getId()))
            throw new ForbiddenException("Emergency request does not belong to this consumer");

        if (emergencyRequest.getStatus() == EmergencyRequestStatus.ACCEPTED
                || emergencyRequest.getStatus() == EmergencyRequestStatus.CANCELLED)
            throw new BadRequestException(
                    "Emergency request cannot be cancelled, it has already been " + emergencyRequest.getStatus());

        emergencyRequest.setStatus(EmergencyRequestStatus.CANCELLED);
        emergencyRequestRepository.save(emergencyRequest);

        // Notify all providers that the emergency request has been canceled
        List<ProviderResponse> allResponses = providerResponseRepository
                .findByEmergencyRequestId(emergencyRequest.getId());
        for (ProviderResponse response : allResponses)
        {
            notificationFactory.send(response.getProvider().getId(),
                    NotificationType.EMERGENCY_ALERT,
                    Map.of(
                            "title", "Request Cancelled",
                            "message", "The emergency request has been cancelled by the consumer",
                            "emergencyRequestId", emergencyRequest.getId().toString()));
        }

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
    public List<EmergencyRequestResponse> getAcceptedEmergencyRequests (Long userId)
    {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<EmergencyRequest> emergencyRequests;
        if (user.getRole() == UserType.CONSUMER)
        {
            emergencyRequests = emergencyRequestRepository
                    .findByConsumerIdAndStatusInAndSelectedProviderNotNullOrderByCreatedAtDesc
                            (userId, List.of(EmergencyRequestStatus.ACCEPTED));
        }
        else if (user.getRole() == UserType.PROVIDER)
        {
            emergencyRequests = emergencyRequestRepository
                    .findBySelectedProviderIdAndStatusInOrderByCreatedAtDesc
                            (userId, List.of(EmergencyRequestStatus.ACCEPTED));
        }
        else
            throw new ForbiddenException("User is not a provider or a consumer");

        return emergencyRequests.stream().map(emergencyRequestMapper::toEmergencyRequestResponse).toList();
    }

    @Override
    public List<EmergencyRequestResponse> getEmergencyRequestsHistory(Long userId)
    {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<EmergencyRequest> emergencyRequests;
        if (user.getRole() == UserType.CONSUMER)
        {
            emergencyRequests = emergencyRequestRepository
                    .findByConsumerIdAndStatusInAndSelectedProviderNotNullOrderByCreatedAtDesc
                            (userId, List.of(EmergencyRequestStatus.COMPLETED, EmergencyRequestStatus.EXPIRED));
        }
        else if (user.getRole() == UserType.PROVIDER)
        {
            emergencyRequests = emergencyRequestRepository
                    .findBySelectedProviderIdAndStatusInOrderByCreatedAtDesc
                            (userId, List.of(EmergencyRequestStatus.COMPLETED, EmergencyRequestStatus.EXPIRED));
        }
        else
            throw new ForbiddenException("User is not a provider or a consumer");

        return emergencyRequests.stream().map(emergencyRequestMapper::toEmergencyRequestResponse).toList();
    }

    @Override
    @Transactional
    public EmergencyRequestResponse submitEmergencyRequestRating(Long consumerId, EmergencyRatingRequest ratingRequest) {
        EmergencyRequest emergencyRequest = emergencyRequestRepository.findById(ratingRequest.getEmergencyRequestId())
                .orElseThrow(() -> new ResourceNotFoundException("Emergency request not found"));

        if (!consumerId.equals(emergencyRequest.getConsumer().getId()))
            throw new ForbiddenException("Emergency request does not belong to this consumer");

        EmergencyRequestStatus status = emergencyRequest.getStatus();
        boolean isCompleted = status == EmergencyRequestStatus.COMPLETED;
        boolean isExpiredAndAccepted = status == EmergencyRequestStatus.EXPIRED && emergencyRequest.getSelectedProvider() != null;

        if (!isCompleted && !isExpiredAndAccepted) {
            throw new BadRequestException("Rating and reviews are only allowed for completed emergency requests or expired emergency requests that were accepted.");
        }

        if (emergencyRequest.getConsumerRating() != null)
            throw new BadRequestException("You have already rated this emergency request");

        emergencyRequest.setPunctualityRating(ratingRequest.getPunctualityRating().doubleValue());
        emergencyRequest.setCommitmentRating(ratingRequest.getCommitmentRating().doubleValue());
        emergencyRequest.setQualityOfWorkRating(ratingRequest.getQualityOfWorkRating().doubleValue());

        Double overallRating = (emergencyRequest.getPunctualityRating() + emergencyRequest.getCommitmentRating()
                + emergencyRequest.getQualityOfWorkRating()) / 3.0;
        overallRating = Math.round(overallRating * 10.0) / 10.0;
        emergencyRequest.setConsumerRating(overallRating);
        emergencyRequest.setConsumerReview(ratingRequest.getReview() != null && !ratingRequest.getReview().trim().isEmpty() ? ratingRequest.getReview() : null);

        emergencyRequestRepository.save(emergencyRequest);

        // Update provider averages
        Provider provider = emergencyRequest.getSelectedProvider();
        if (provider != null) {
            long ratedBookingsCount = bookingRepository.countByProviderIdAndConsumerRatingIsNotNull(provider.getId());
            long ratedEmergencyRequestsCount = emergencyRequestRepository.countBySelectedProviderIdAndConsumerRatingIsNotNull(provider.getId());
            long totalRatedCount = ratedBookingsCount + ratedEmergencyRequestsCount;

            if (totalRatedCount <= 1 || provider.getAverageRating() == null || provider.getAverageRating() == 0.0) {
                provider.setAveragePunctualityRating(emergencyRequest.getPunctualityRating());
                provider.setAverageCommitmentRating(emergencyRequest.getCommitmentRating());
                provider.setAverageQualityOfWorkRating(emergencyRequest.getQualityOfWorkRating());
                provider.setAverageRating(overallRating);
            } else {
                long oldCount = totalRatedCount - 1;
                if (oldCount < 1) oldCount = 1;

                double oldPunctuality = provider.getAveragePunctualityRating() != null ? provider.getAveragePunctualityRating() : 0.0;
                double oldCommitment = provider.getAverageCommitmentRating() != null ? provider.getAverageCommitmentRating() : 0.0;
                double oldQuality = provider.getAverageQualityOfWorkRating() != null ? provider.getAverageQualityOfWorkRating() : 0.0;
                double oldOverall = provider.getAverageRating() != null ? provider.getAverageRating() : 0.0;

                provider.setAveragePunctualityRating(((oldPunctuality * oldCount) + emergencyRequest.getPunctualityRating()) / totalRatedCount);
                provider.setAverageCommitmentRating(((oldCommitment * oldCount) + emergencyRequest.getCommitmentRating()) / totalRatedCount);
                provider.setAverageQualityOfWorkRating(((oldQuality * oldCount) + emergencyRequest.getQualityOfWorkRating()) / totalRatedCount);
                provider.setAverageRating(((oldOverall * oldCount) + overallRating) / totalRatedCount);
            }
            updateProviderRates(provider);
            providerRepository.save(provider);
        }

        return emergencyRequestMapper.toEmergencyRequestResponse(emergencyRequest);
    }

    @Override
    @Transactional
    public EmergencyRequestResponse submitConsumerEmergencyRequestRating(Long providerId, ProviderEmergencyRatingRequest ratingRequest) {
        EmergencyRequest emergencyRequest = emergencyRequestRepository.findById(ratingRequest.getEmergencyRequestId())
                .orElseThrow(() -> new ResourceNotFoundException("Emergency request not found"));

        if (emergencyRequest.getSelectedProvider() == null || !providerId.equals(emergencyRequest.getSelectedProvider().getId()))
            throw new ForbiddenException("Emergency request does not belong to this provider");

        EmergencyRequestStatus status = emergencyRequest.getStatus();
        boolean isCompleted = status == EmergencyRequestStatus.COMPLETED;
        boolean isExpiredAndAccepted = status == EmergencyRequestStatus.EXPIRED && emergencyRequest.getSelectedProvider() != null;

        if (!isCompleted && !isExpiredAndAccepted) {
            throw new BadRequestException("Rating and reviews are only allowed for completed emergency requests or expired emergency requests that were accepted.");
        }

        if (emergencyRequest.getProviderRating() != null)
            throw new BadRequestException("You have already rated this emergency request");

        emergencyRequest.setProviderRating(ratingRequest.getRating().doubleValue());
        emergencyRequest.setProviderReview(ratingRequest.getReview() != null && !ratingRequest.getReview().trim().isEmpty() ? ratingRequest.getReview() : null);

        emergencyRequestRepository.save(emergencyRequest);

        Provider provider = emergencyRequest.getSelectedProvider();
        if (provider != null) {
            updateProviderRates(provider);
            providerRepository.save(provider);
        }

        // Update consumer average
        Consumer consumer = emergencyRequest.getConsumer();
        if (consumer != null) {
            long ratedBookingsCount = bookingRepository.countByConsumerIdAndProviderRatingIsNotNull(consumer.getId());
            long ratedEmergencyRequestsCount = emergencyRequestRepository.countByConsumerIdAndProviderRatingIsNotNull(consumer.getId());
            long totalRatedCount = ratedBookingsCount + ratedEmergencyRequestsCount;

            if (totalRatedCount <= 1 || consumer.getAverageRating() == null || consumer.getAverageRating() == 0.0) {
                consumer.setAverageRating(emergencyRequest.getProviderRating());
            } else {
                long oldCount = totalRatedCount - 1;
                if (oldCount < 1) oldCount = 1;
                double oldOverall = consumer.getAverageRating() != null ? consumer.getAverageRating() : 0.0;
                consumer.setAverageRating(((oldOverall * oldCount) + emergencyRequest.getProviderRating()) / totalRatedCount);
            }
            consumerRepository.save(consumer);
        }

        return emergencyRequestMapper.toEmergencyRequestResponse(emergencyRequest);
    }

    private void updateProviderRates(Provider provider) {
        int acceptanceRate;
        int bookingRate;

        Integer totalRequests = provider.getTotalRequests();
        if (totalRequests != null && totalRequests > 0) {
            Integer accepted = provider.getTotalBookings() != null ? provider.getTotalBookings() : 0;
            acceptanceRate = clampRate((accepted * 100) / totalRequests);

            Integer completed = provider.getCompletedJobs() != null ? provider.getCompletedJobs() : 0;
            bookingRate = clampRate((completed * 100) / totalRequests);
        } else {
            acceptanceRate = 100;
            bookingRate = 0;
        }

        provider.setAcceptanceRate(acceptanceRate);
        provider.setBookingRate(bookingRate);
    }

    private void updateConsumerRates(Consumer consumer) {
        consumer.setCancellationRate(consumer.getCancellationRate()); // This uses the helper method to get the value
    }

    private int clampRate(int value) {
        return Math.max(0, Math.min(100, value));
    }
}
