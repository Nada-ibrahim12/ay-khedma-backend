package com.aykhedma.controller;

import com.aykhedma.config.TestSecurityConfig;
import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.response.DistanceResponse;
import com.aykhedma.dto.response.LocationResponse;
import com.aykhedma.dto.response.ProviderSummaryResponse;
import com.aykhedma.exception.GlobalExceptionHandler;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.service.LocationService;
import com.aykhedma.util.TestDataFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LocationController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("Location Controller Unit Tests")
class LocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LocationService locationService;

    private LocationDTO locationDTO;
    private LocationResponse locationResponse;

    private final Long CONSUMER_ID = 1L;
    private final Long PROVIDER_ID = 2L;

    @BeforeEach
    void setUp() {
        locationDTO = TestDataFactory.createLocationDTO();
        locationResponse = LocationResponse.builder()
                .id(10L)
                .latitude(locationDTO.getLatitude())
                .longitude(locationDTO.getLongitude())
                .success(true)
                .message("OK")
                .build();
    }

    @Test
    @DisplayName("POST /api/v1/locations/consumers/{id} - Should save consumer location")
    void saveConsumerLocation_ShouldReturnCreated() throws Exception {
        when(locationService.saveConsumerLocation(eq(CONSUMER_ID), any(LocationDTO.class)))
                .thenReturn(locationResponse);

        mockMvc.perform(post("/api/v1/locations/consumers/{id}", CONSUMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(locationDTO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/locations/consumers/{id} - Should return 404 when consumer not found")
    void saveConsumerLocation_NotFound_Returns404() throws Exception {
        when(locationService.saveConsumerLocation(eq(CONSUMER_ID), any(LocationDTO.class)))
                .thenThrow(new ResourceNotFoundException("Consumer not found with id: " + CONSUMER_ID));

        mockMvc.perform(post("/api/v1/locations/consumers/{id}", CONSUMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(locationDTO)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Consumer not found with id: " + CONSUMER_ID));
    }

    @Test
    @DisplayName("POST /api/v1/locations/consumers/{id} - Should return 400 for invalid data")
    void saveConsumerLocation_InvalidData_Returns400() throws Exception {
        LocationDTO invalidDto = LocationDTO.builder()
                .latitude(100.0)
                .longitude(200.0)
                .build();

        mockMvc.perform(post("/api/v1/locations/consumers/{id}", CONSUMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.message").value("Invalid input parameters"))
                .andExpect(jsonPath("$.validationErrors.latitude").value("Latitude must be between -90 and 90"))
                .andExpect(jsonPath("$.validationErrors.longitude").value("Longitude must be between -180 and 180"));
    }

    @Test
    @DisplayName("PUT /api/v1/locations/consumers/{id} - Should update consumer location")
    void updateConsumerLocation_ShouldReturnOk() throws Exception {
        when(locationService.updateConsumerLocation(eq(CONSUMER_ID), any(LocationDTO.class)))
                .thenReturn(locationResponse);

        mockMvc.perform(put("/api/v1/locations/consumers/{id}", CONSUMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(locationDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10L));
    }

    @Test
    @DisplayName("PUT /api/v1/locations/consumers/{id} - Should return 404 when consumer not found")
    void updateConsumerLocation_NotFound_Returns404() throws Exception {
        when(locationService.updateConsumerLocation(eq(CONSUMER_ID), any(LocationDTO.class)))
                .thenThrow(new ResourceNotFoundException("Consumer not found with id: " + CONSUMER_ID));

        mockMvc.perform(put("/api/v1/locations/consumers/{id}", CONSUMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(locationDTO)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Consumer not found with id: " + CONSUMER_ID));
    }

    @Test
    @DisplayName("PATCH /api/v1/locations/consumers/{id} - Should patch consumer location")
    void patchConsumerLocation_ShouldReturnOk() throws Exception {
        when(locationService.patchConsumerLocation(eq(CONSUMER_ID), any(LocationDTO.class)))
                .thenReturn(locationResponse);

        mockMvc.perform(patch("/api/v1/locations/consumers/{id}", CONSUMER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(locationDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/locations/consumers/{id} - Should get consumer location")
    void getConsumerLocation_ShouldReturnOk() throws Exception {
        when(locationService.getConsumerLocation(CONSUMER_ID)).thenReturn(locationDTO);

        mockMvc.perform(get("/api/v1/locations/consumers/{id}", CONSUMER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(locationDTO.getLatitude()));
    }

    @Test
    @DisplayName("GET /api/v1/locations/consumers/{id} - Should return 404 when consumer not found")
    void getConsumerLocation_NotFound_Returns404() throws Exception {
        when(locationService.getConsumerLocation(CONSUMER_ID))
                .thenThrow(new ResourceNotFoundException("Consumer not found with id: " + CONSUMER_ID));

        mockMvc.perform(get("/api/v1/locations/consumers/{id}", CONSUMER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Consumer not found with id: " + CONSUMER_ID));
    }

    @Test
    @DisplayName("POST /api/v1/locations/providers/{id} - Should save provider location")
    void saveProviderLocation_ShouldReturnCreated() throws Exception {
        when(locationService.saveProviderLocation(eq(PROVIDER_ID), any(LocationDTO.class)))
                .thenReturn(locationResponse);

        mockMvc.perform(post("/api/v1/locations/providers/{id}", PROVIDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(locationDTO)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/v1/locations/providers/{id} - Should return 400 for invalid data")
    void saveProviderLocation_InvalidData_Returns400() throws Exception {
        LocationDTO invalidDto = LocationDTO.builder()
                .latitude(100.0)
                .longitude(200.0)
                .build();

        mockMvc.perform(post("/api/v1/locations/providers/{id}", PROVIDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.message").value("Invalid input parameters"))
                .andExpect(jsonPath("$.validationErrors.latitude").value("Latitude must be between -90 and 90"))
                .andExpect(jsonPath("$.validationErrors.longitude").value("Longitude must be between -180 and 180"));
    }

    @Test
    @DisplayName("POST /api/v1/locations/providers/{id} - Should return 404 when provider not found")
    void saveProviderLocation_NotFound_Returns404() throws Exception {
        when(locationService.saveProviderLocation(eq(PROVIDER_ID), any(LocationDTO.class)))
                .thenThrow(new ResourceNotFoundException("Provider not found with id: " + PROVIDER_ID));

        mockMvc.perform(post("/api/v1/locations/providers/{id}", PROVIDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(locationDTO)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Provider not found with id: " + PROVIDER_ID));
    }

    @Test
    @DisplayName("GET /api/v1/locations/providers/{id} - Should get provider location")
    void getProviderLocation_ShouldReturnOk() throws Exception {
        when(locationService.getProviderLocation(PROVIDER_ID)).thenReturn(locationDTO);

        mockMvc.perform(get("/api/v1/locations/providers/{id}", PROVIDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.longitude").value(locationDTO.getLongitude()));
    }

    @Test
    @DisplayName("PUT /api/v1/locations/providers/{id} - Should return 400 for invalid data")
    void updateProviderLocation_InvalidData_Returns400() throws Exception {
        LocationDTO invalidDto = LocationDTO.builder()
                .latitude(100.0)
                .longitude(200.0)
                .build();

        mockMvc.perform(put("/api/v1/locations/providers/{id}", PROVIDER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.message").value("Invalid input parameters"))
                .andExpect(jsonPath("$.validationErrors.latitude").value("Latitude must be between -90 and 90"))
                .andExpect(jsonPath("$.validationErrors.longitude").value("Longitude must be between -180 and 180"));
    }

    @Test
    @DisplayName("GET /api/v1/locations/providers/{id} - Should return 404 when provider not found")
    void getProviderLocation_NotFound_Returns404() throws Exception {
        when(locationService.getProviderLocation(PROVIDER_ID))
                .thenThrow(new ResourceNotFoundException("Provider not found with id: " + PROVIDER_ID));

        mockMvc.perform(get("/api/v1/locations/providers/{id}", PROVIDER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Provider not found with id: " + PROVIDER_ID));
    }

    @Test
    @DisplayName("GET /api/v1/locations/distance/consumer/{c}/provider/{p} - Should calculate distance")
    void calculateDistance_ShouldReturnOk() throws Exception {
        DistanceResponse distanceResponse = DistanceResponse.builder()
                .consumerId(CONSUMER_ID)
                .providerId(PROVIDER_ID)
                .distanceKm(1.2)
                .build();

        when(locationService.calculateDistanceBetweenConsumerAndProvider(CONSUMER_ID, PROVIDER_ID))
                .thenReturn(distanceResponse);

        mockMvc.perform(get("/api/v1/locations/distance/consumer/{c}/provider/{p}", CONSUMER_ID, PROVIDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distanceKm").value(1.2));
    }

    @Test
    @DisplayName("GET /api/v1/locations/distance/consumer/{c}/provider/{p} - Should return 404 when consumer missing")
    void calculateDistance_ConsumerNotFound_Returns404() throws Exception {
        when(locationService.calculateDistanceBetweenConsumerAndProvider(CONSUMER_ID, PROVIDER_ID))
                .thenThrow(new ResourceNotFoundException("Consumer not found with id: " + CONSUMER_ID));

        mockMvc.perform(get("/api/v1/locations/distance/consumer/{c}/provider/{p}", CONSUMER_ID, PROVIDER_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Consumer not found with id: " + CONSUMER_ID));
    }

    @Test
    @DisplayName("GET /api/v1/locations/provider/{id}/service-area-check - Should return boolean")
    void isLocationWithinServiceArea_ShouldReturnOk() throws Exception {
        when(locationService.isLocationWithinServiceArea(PROVIDER_ID, 30.0, 31.0)).thenReturn(true);

        mockMvc.perform(get("/api/v1/locations/provider/{id}/service-area-check", PROVIDER_ID)
                        .param("latitude", "30.0")
                        .param("longitude", "31.0"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    @DisplayName("GET /api/v1/locations/provider/{id}/service-area-check - Should return 404 when provider not found")
    void isLocationWithinServiceArea_NotFound_Returns404() throws Exception {
        when(locationService.isLocationWithinServiceArea(PROVIDER_ID, 30.0, 31.0))
                .thenThrow(new ResourceNotFoundException("Provider not found with id: " + PROVIDER_ID));

        mockMvc.perform(get("/api/v1/locations/provider/{id}/service-area-check", PROVIDER_ID)
                        .param("latitude", "30.0")
                        .param("longitude", "31.0"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Provider not found with id: " + PROVIDER_ID));
    }

    @Test
    @DisplayName("GET /api/v1/locations/provider/{id}/service-area/consumer/{cid} - Should return boolean")
    void isConsumerWithinProviderServiceArea_ShouldReturnOk() throws Exception {
        when(locationService.getConsumerLocation(CONSUMER_ID)).thenReturn(locationDTO);
        when(locationService.isLocationWithinServiceArea(PROVIDER_ID, locationDTO.getLatitude(), locationDTO.getLongitude()))
                .thenReturn(true);

        mockMvc.perform(get("/api/v1/locations/provider/{id}/service-area/consumer/{cid}", PROVIDER_ID, CONSUMER_ID))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    @DisplayName("GET /api/v1/locations/providers/nearby - Should return list")
    void findNearbyProviders_ShouldReturnOk() throws Exception {
        List<ProviderSummaryResponse> providers = List.of(
                TestDataFactory.createProviderSummaryResponse(1L),
                TestDataFactory.createProviderSummaryResponse(2L)
        );

        when(locationService.findNearbyProviders(30.0, 31.0, 5.0, null)).thenReturn(providers);

        mockMvc.perform(get("/api/v1/locations/providers/nearby")
                        .param("latitude", "30.0")
                        .param("longitude", "31.0")
                        .param("radius", "5.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
