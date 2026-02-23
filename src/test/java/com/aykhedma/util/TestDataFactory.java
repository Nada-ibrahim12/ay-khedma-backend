package com.aykhedma.util;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.request.ConsumerProfileRequest;
import com.aykhedma.dto.response.ConsumerResponse;
import com.aykhedma.dto.response.ProviderSummaryResponse;
import com.aykhedma.model.location.Location;
import com.aykhedma.model.service.PriceType;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.user.UserType;
import lombok.experimental.UtilityClass;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@UtilityClass
public class TestDataFactory {

    /**
     * Creates a Consumer entity with test data
     */
    public static Consumer createConsumer(Long id) {
        Consumer consumer = Consumer.builder()
                .id(id)
                .name("Test Consumer " + (id != null ? id : ""))
                .email((id != null ? "consumer" + id : "test") + "@example.com")
                .phoneNumber("0123456789" + (id != null ? id % 10 : "0"))
                .password("$2a$10$hashedPassword12345678901234567890")
                .role(UserType.CONSUMER)
                .preferredLanguage("en")
                .profileImage("http://test.com/image.jpg")
                .averageRating(4.5)
                .totalBookings(10)
                .enabled(true)
                .credentialsNonExpired(true)
                .createdAt(LocalDateTime.now())
                .savedProviders(new ArrayList<>())
                .bookings(new ArrayList<>())
                .build();

        return consumer;
    }

    /**
     * Creates a Consumer with location
     */
    public static Consumer createConsumerWithLocation(Long id) {
        Consumer consumer = createConsumer(id);
        consumer.setLocation(createLocation(id != null ? id : 1L));
        return consumer;
    }

    /**
     * Creates a Location entity
     */
    public static Location createLocation(Long id) {
        return Location.builder()
                .id(id)
                .latitude(30.0444)
                .longitude(31.2357)
                .address("123 Test Street")
                .area("Maadi")
                .city("Cairo")
                .build();
    }

    /**
     * Creates a LocationDTO
     */
    public static LocationDTO createLocationDTO() {
        return LocationDTO.builder()
                .latitude(30.0444)
                .longitude(31.2357)
                .address("123 Test Street")
                .area("Maadi")
                .city("Cairo")
                .build();
    }

    /**
     * Creates a ConsumerProfileRequest with all fields
     */
    public static ConsumerProfileRequest createConsumerProfileRequest() {
        return ConsumerProfileRequest.builder()
                .name("Updated Name")
                .email("updated@example.com")
                .phoneNumber("01234567890")
                .preferredLanguage("ar-EG")
                .location(createLocationDTO())
                .build();
    }

    /**
     * Creates a partial ConsumerProfileRequest (only name)
     */
    public static ConsumerProfileRequest createPartialProfileRequest() {
        return ConsumerProfileRequest.builder()
                .name("Only Name Updated")
                .build();
    }

    /**
     * Creates a Provider entity with test data
     * FIXED: Handle null id properly
     */
    public static Provider createProvider(Long id) {
        Provider provider = Provider.builder()
                .id(id)
                .name("Test Provider " + (id != null ? id : ""))
                .email((id != null ? "provider" + id : "test-provider") + "@example.com")
                .phoneNumber("0123456789" + (id != null ? id % 10 : "0"))
                .password("$2a$10$hashedPassword12345678901234567890")
                .role(UserType.PROVIDER)
                .profileImage("http://test.com/provider.jpg")
                .averageRating(4.8)
                .completedJobs(50)
                .price(100.0)
                .priceType(PriceType.valueOf("HOUR"))
                .build();

        return provider;
    }

    /**
     * Creates a ProviderSummaryResponse
     */
    public static ProviderSummaryResponse createProviderSummaryResponse(Long id) {
        return ProviderSummaryResponse.builder()
                .id(id)
                .name("Test Provider " + id)
                .profileImage("http://test.com/provider.jpg")
                .serviceType("Plumbing")
                .averageRating(4.8)
                .completedJobs(50)
                .price(100.0)
                .priceType(PriceType.valueOf("HOUR"))
                .build();
    }

    /**
     * Creates a ConsumerResponse
     */
    public static ConsumerResponse createConsumerResponse(Long id) {
        return ConsumerResponse.builder()
                .id(id)
                .name("Test Consumer " + id)
                .email("consumer" + id + "@example.com")
                .phoneNumber("0123456789" + (id % 10))
                .profileImage("http://test.com/image.jpg")
                .preferredLanguage("en")
                .role(UserType.CONSUMER)
                .location(createLocationDTO())
                .averageRating(4.5)
                .totalBookings(10)
                .savedProviders(List.of(createProviderSummaryResponse(1L)))
                .build();
    }

    /**
     * Creates a list of Consumer entities
     */
    public static List<Consumer> createConsumerList(int count) {
        List<Consumer> consumers = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            consumers.add(createConsumer((long) i));
        }
        return consumers;
    }
}