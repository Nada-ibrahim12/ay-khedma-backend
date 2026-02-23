//package com.aykhedma.mapper;
//
//import com.aykhedma.dto.location.LocationDTO;
//import com.aykhedma.dto.request.ConsumerProfileRequest;
//import com.aykhedma.dto.response.ConsumerResponse;
//import com.aykhedma.dto.response.ProviderSummaryResponse;
//import com.aykhedma.model.location.Location;
//import com.aykhedma.model.user.Consumer;
//import com.aykhedma.model.user.Provider;
//import com.aykhedma.util.TestDataFactory;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Nested;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.test.context.junit.jupiter.SpringExtension;
//
//import java.util.List;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//@ExtendWith(SpringExtension.class)
//@SpringBootTest(classes = {ConsumerMapper.class, ProviderMapper.class})
//@DisplayName("Consumer Mapper Tests")
//class ConsumerMapperTest {
//
//    @Autowired
//    private ConsumerMapper consumerMapper;
//
//    private Consumer consumer;
//    private Provider provider;
//    private final Long CONSUMER_ID = 1L;
//    private final Long PROVIDER_ID = 2L;
//
//    @BeforeEach
//    void setUp() {
//        consumer = TestDataFactory.createConsumerWithLocation(CONSUMER_ID);
//        provider = TestDataFactory.createProvider(PROVIDER_ID);
//    }
//
//    @Nested
//    @DisplayName("Consumer to ConsumerResponse Mapping Tests")
//    class ConsumerToResponseMappingTests {
//
//        @Test
//        @DisplayName("Should map all basic fields correctly")
//        void toConsumerResponse_mapsBasicFields() {
//            // Act
//            ConsumerResponse response = consumerMapper.toConsumerResponse(consumer);
//
//            // Assert
//            assertThat(response).isNotNull();
//            assertThat(response.getId()).isEqualTo(consumer.getId());
//            assertThat(response.getName()).isEqualTo(consumer.getName());
//            assertThat(response.getEmail()).isEqualTo(consumer.getEmail());
//            assertThat(response.getPhoneNumber()).isEqualTo(consumer.getPhoneNumber());
//            assertThat(response.getProfileImage()).isEqualTo(consumer.getProfileImage());
//            assertThat(response.getPreferredLanguage()).isEqualTo(consumer.getPreferredLanguage());
//            assertThat(response.getRole()).isEqualTo(consumer.getRole());
//            assertThat(response.getAverageRating()).isEqualTo(consumer.getAverageRating());
//            assertThat(response.getTotalBookings()).isEqualTo(consumer.getTotalBookings());
//        }
//
//        @Test
//        @DisplayName("Should map location correctly")
//        void toConsumerResponse_mapsLocation() {
//            // Act
//            ConsumerResponse response = consumerMapper.toConsumerResponse(consumer);
//
//            // Assert
//            assertThat(response.getLocation()).isNotNull();
//            assertThat(response.getLocation().getLatitude()).isEqualTo(consumer.getLocation().getLatitude());
//            assertThat(response.getLocation().getLongitude()).isEqualTo(consumer.getLocation().getLongitude());
//        }
//
//        @Test
//        @DisplayName("Should map saved providers list correctly")
//        void toConsumerResponse_mapsSavedProviders() {
//            // Arrange
//            consumer.getSavedProviders().add(provider);
//
//            // Act
//            ConsumerResponse response = consumerMapper.toConsumerResponse(consumer);
//
//            // Assert
//            assertThat(response.getSavedProviders()).isNotNull();
//            assertThat(response.getSavedProviders()).hasSize(1);
//        }
//    }
//
//    @Nested
//    @DisplayName("Request to Entity Mapping Tests")
//    class RequestToEntityMappingTests {
//
//        @Test
//        @DisplayName("Should map request fields to entity")
//        void toEntity_mapsRequestFields() {
//            // Arrange
//            ConsumerProfileRequest request = TestDataFactory.createConsumerProfileRequest();
//
//            // Act
//            Consumer entity = consumerMapper.toEntity(request);
//
//            // Assert
//            assertThat(entity).isNotNull();
//            assertThat(entity.getName()).isEqualTo(request.getName());
//            assertThat(entity.getEmail()).isEqualTo(request.getEmail());
//            assertThat(entity.getPhoneNumber()).isEqualTo(request.getPhoneNumber());
//            assertThat(entity.getPreferredLanguage()).isEqualTo(request.getPreferredLanguage());
//        }
//
//        @Test
//        @DisplayName("Should ignore ID and sensitive fields")
//        void toEntity_ignoresSensitiveFields() {
//            // Arrange
//            ConsumerProfileRequest request = TestDataFactory.createConsumerProfileRequest();
//
//            // Act
//            Consumer entity = consumerMapper.toEntity(request);
//
//            // Assert
//            assertThat(entity.getId()).isNull();
//            assertThat(entity.getPassword()).isNull();
//            assertThat(entity.getRole()).isNull();
//            assertThat(entity.getLocation()).isNull();
//        }
//    }
//
//    @Nested
//    @DisplayName("Location Mapping Tests")
//    class LocationMappingTests {
//
//        @Test
//        @DisplayName("Should map Location to LocationDTO")
//        void mapLocationToDTO_mapsAllFields() {
//            // Arrange
//            Location location = TestDataFactory.createLocation(1L);
//
//            // Act
//            LocationDTO dto = consumerMapper.mapLocationToDTO(location);
//
//            // Assert
//            assertThat(dto).isNotNull();
//            assertThat(dto.getLatitude()).isEqualTo(location.getLatitude());
//            assertThat(dto.getLongitude()).isEqualTo(location.getLongitude());
//            assertThat(dto.getAddress()).isEqualTo(location.getAddress());
//            assertThat(dto.getArea()).isEqualTo(location.getArea());
//            assertThat(dto.getCity()).isEqualTo(location.getCity());
//        }
//
//        @Test
//        @DisplayName("Should return null when mapping null Location")
//        void mapLocationToDTO_nullLocation_returnsNull() {
//            // Act
//            LocationDTO dto = consumerMapper.mapLocationToDTO(null);
//
//            // Assert
//            assertThat(dto).isNull();
//        }
//    }
//}