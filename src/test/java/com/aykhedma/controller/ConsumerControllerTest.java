//package com.aykhedma.controller;
//
//import com.aykhedma.config.TestSecurityConfig;
//import com.aykhedma.dto.request.ConsumerProfileRequest;
//import com.aykhedma.dto.response.ConsumerResponse;
//import com.aykhedma.dto.response.ProfileResponse;
//import com.aykhedma.dto.response.ProviderSummaryResponse;
//import com.aykhedma.service.ConsumerService;
//import com.aykhedma.util.TestDataFactory;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
//import org.springframework.boot.test.mock.mockito.MockBean;
//import org.springframework.context.annotation.Import;
//import org.springframework.http.MediaType;
//import org.springframework.mock.web.MockMultipartFile;
//import org.springframework.test.web.servlet.MockMvc;
//
//import java.util.List;
//
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.Mockito.when;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
//
//@WebMvcTest(ConsumerController.class)
//@Import(TestSecurityConfig.class) // Import test security config
//@DisplayName("Consumer Controller Unit Tests")
//class ConsumerControllerTest {
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    @MockBean
//    private ConsumerService consumerService;
//
//    private ConsumerResponse consumerResponse;
//    private ConsumerProfileRequest profileRequest;
//    private ProfileResponse profileResponse;
//    private final Long CONSUMER_ID = 1L;
//    private final Long PROVIDER_ID = 2L;
//
//    @BeforeEach
//    void setUp() {
//        consumerResponse = TestDataFactory.createConsumerResponse(CONSUMER_ID);
//        profileRequest = TestDataFactory.createConsumerProfileRequest();
//        profileResponse = ProfileResponse.builder()
//                .success(true)
//                .message("Success")
//                .id(PROVIDER_ID)
//                .build();
//    }
//
//    @Test
//    @DisplayName("GET /api/consumers/{id} - Should return consumer profile")
//    void getConsumerProfile_ShouldReturnConsumer() throws Exception {
//        // Arrange
//        when(consumerService.getConsumerProfile(CONSUMER_ID)).thenReturn(consumerResponse);
//
//        // Act & Assert
//        mockMvc.perform(get("/api/consumers/{id}", CONSUMER_ID))
//                .andExpect(status().isOk())
//                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
//                .andExpect(jsonPath("$.id").value(CONSUMER_ID))
//                .andExpect(jsonPath("$.name").value(consumerResponse.getName()))
//                .andExpect(jsonPath("$.email").value(consumerResponse.getEmail()));
//    }
//
//    @Test
//    @DisplayName("PUT /api/consumers/{id} - Should update consumer profile")
//    void updateConsumerProfile_ShouldReturnUpdatedConsumer() throws Exception {
//        // Arrange
//        when(consumerService.updateConsumerProfile(eq(CONSUMER_ID), any(ConsumerProfileRequest.class)))
//                .thenReturn(consumerResponse);
//
//        // Act & Assert
//        mockMvc.perform(put("/api/consumers/{id}", CONSUMER_ID)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(profileRequest)))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.id").value(CONSUMER_ID));
//    }
//
//    @Test
//    @DisplayName("POST /api/consumers/{id}/saved-providers/{providerId} - Should save provider")
//    void saveProvider_ShouldReturnSuccessResponse() throws Exception {
//        // Arrange
//        when(consumerService.saveProvider(CONSUMER_ID, PROVIDER_ID)).thenReturn(profileResponse);
//
//        // Act & Assert
//        mockMvc.perform(post("/api/consumers/{id}/saved-providers/{providerId}", CONSUMER_ID, PROVIDER_ID))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.success").value(true))
//                .andExpect(jsonPath("$.message").value("Success"));
//    }
//
//    @Test
//    @DisplayName("DELETE /api/consumers/{id}/saved-providers/{providerId} - Should remove provider")
//    void removeSavedProvider_ShouldReturnSuccessResponse() throws Exception {
//        // Arrange
//        when(consumerService.removeSavedProvider(CONSUMER_ID, PROVIDER_ID)).thenReturn(profileResponse);
//
//        // Act & Assert
//        mockMvc.perform(delete("/api/consumers/{id}/saved-providers/{providerId}", CONSUMER_ID, PROVIDER_ID))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.success").value(true));
//    }
//
//    @Test
//    @DisplayName("GET /api/consumers/{id}/saved-providers - Should return saved providers")
//    void getSavedProviders_ShouldReturnList() throws Exception {
//        // Arrange
//        List<ProviderSummaryResponse> providers = List.of(
//                TestDataFactory.createProviderSummaryResponse(1L),
//                TestDataFactory.createProviderSummaryResponse(2L)
//        );
//        when(consumerService.getSavedProviders(CONSUMER_ID)).thenReturn(providers);
//
//        // Act & Assert
//        mockMvc.perform(get("/api/consumers/{id}/saved-providers", CONSUMER_ID))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$").isArray())
//                .andExpect(jsonPath("$.length()").value(2))
//                .andExpect(jsonPath("$[0].id").value(1L))
//                .andExpect(jsonPath("$[1].id").value(2L));
//    }
//
//    @Test
//    @DisplayName("POST /api/consumers/{id}/profile-picture - Should upload profile picture")
//    void updateProfilePicture_ShouldReturnUpdatedConsumer() throws Exception {
//        // Arrange
//        MockMultipartFile file = new MockMultipartFile(
//                "file",
//                "test.jpg",
//                MediaType.IMAGE_JPEG_VALUE,
//                "test image content".getBytes()
//        );
//
//        when(consumerService.updateProfilePicture(eq(CONSUMER_ID), any())).thenReturn(consumerResponse);
//
//        // Act & Assert
//        mockMvc.perform(multipart("/api/consumers/{id}/profile-picture", CONSUMER_ID)
//                        .file(file)
//                        .with(request -> {
//                            request.setMethod("POST");
//                            return request;
//                        }))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.id").value(CONSUMER_ID));
//    }
//}