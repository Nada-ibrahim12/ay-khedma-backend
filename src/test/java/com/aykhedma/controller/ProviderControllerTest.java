package com.aykhedma.controller;

import com.aykhedma.config.TestSecurityConfig;
import com.aykhedma.dto.request.ProviderProfileRequest;
import com.aykhedma.dto.request.WorkingDayRequest;
import com.aykhedma.dto.response.*;
import com.aykhedma.exception.GlobalExceptionHandler;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.model.user.VerificationStatus;
import com.aykhedma.service.ProviderService;
import com.aykhedma.util.TestDataFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestSecurityConfig.class, GlobalExceptionHandler.class })
@DisplayName("Provider Controller Integration Tests")
class ProviderControllerTest {

        private static final class PrincipalUser {
                private final Long id;

                private PrincipalUser(Long id) {
                        this.id = id;
                }

                public Long getId() {
                        return id;
                }
        }

        private static final class PrincipalPayload {
                private final PrincipalUser user;

                private PrincipalPayload(Long userId) {
                        this.user = new PrincipalUser(userId);
                }

                public PrincipalUser getUser() {
                        return user;
                }
        }

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockBean
        private ProviderService providerService;

        private ProviderResponse providerResponse;
        private ProviderProfileRequest profileRequest;
        private ProfileResponse profileResponse;

        private final Long PROVIDER_ID = 1L;
        private final Long CONSUMER_ID = 1L;
        private final Long TIME_SLOT_ID = 10L;
        private final Long DOCUMENT_ID = 20L;

        private RequestPostProcessor authenticatedProvider() {
                var principal = new PrincipalPayload(PROVIDER_ID);
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_PROVIDER"));
                var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                return SecurityMockMvcRequestPostProcessors.authentication(auth);
        }

        private RequestPostProcessor authenticatedConsumer() {
                var principal = new PrincipalPayload(CONSUMER_ID);
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_CONSUMER"));
                var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                return SecurityMockMvcRequestPostProcessors.authentication(auth);
        }

        @BeforeEach
        void setUp() {
                providerResponse = ProviderResponse.builder()
                                .id(PROVIDER_ID)
                                .name("Test Provider")
                                .email("provider@example.com")
                                .workLocation("Nasr City, Cairo")
                                .build();

                profileRequest = ProviderProfileRequest.builder()
                                .name("Updated Provider")
                                .email("updated@example.com")
                                .workLocation("Nasr City, Cairo")
                                .build();

                profileResponse = ProfileResponse.builder()
                                .success(true)
                                .message("Success")
                                .build();
        }

        @Test
        @DisplayName("GET /api/v1/providers/{id} - Should return provider profile")
        void getProviderProfile_ShouldReturnProvider() throws Exception {
                when(providerService.getProviderProfile(PROVIDER_ID)).thenReturn(providerResponse);

                mockMvc.perform(get("/api/v1/providers/{id}", PROVIDER_ID)
                                .with(authenticatedConsumer()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(PROVIDER_ID))
                                .andExpect(jsonPath("$.name").value(providerResponse.getName()));
        }

        @Test
        @DisplayName("GET /api/v1/providers/{id} - Should return 404 when provider not found")
        void getProviderProfile_NotFound_Returns404() throws Exception {
                when(providerService.getProviderProfile(PROVIDER_ID))
                                .thenThrow(new ResourceNotFoundException("Provider not found with id: " + PROVIDER_ID));

                mockMvc.perform(get("/api/v1/providers/{id}", PROVIDER_ID)
                                .with(authenticatedConsumer()))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.status").value(404))
                                .andExpect(jsonPath("$.error").value("Not Found"))
                                .andExpect(jsonPath("$.message").value("Provider not found with id: " + PROVIDER_ID));
        }

        @Test
        @DisplayName("GET /api/v1/providers/me - Should return my provider profile")
        void getMyProviderProfile_ShouldReturnProvider() throws Exception {
                when(providerService.getProviderProfile(PROVIDER_ID)).thenReturn(providerResponse);

                mockMvc.perform(get("/api/v1/providers/me")
                                .with(authenticatedProvider()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(PROVIDER_ID));
        }

        @Test
        @DisplayName("PUT /api/v1/providers/me - Should update provider profile")
        void updateProviderProfile_ShouldReturnUpdatedProvider() throws Exception {
                when(providerService.updateProviderProfile(eq(PROVIDER_ID), any(ProviderProfileRequest.class)))
                                .thenReturn(providerResponse);

                mockMvc.perform(put("/api/v1/providers/me")
                                .with(authenticatedProvider())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(profileRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(PROVIDER_ID))
                                .andExpect(jsonPath("$.workLocation").value("Nasr City, Cairo"));
        }

        @Test
        @DisplayName("PUT /api/v1/providers/me - Should return 404 when provider not found")
        void updateProviderProfile_NotFound_Returns404() throws Exception {
                when(providerService.updateProviderProfile(eq(PROVIDER_ID), any(ProviderProfileRequest.class)))
                                .thenThrow(new ResourceNotFoundException("Provider not found with id: " + PROVIDER_ID));

                mockMvc.perform(put("/api/v1/providers/me")
                                .with(authenticatedProvider())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(profileRequest)))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.status").value(404))
                                .andExpect(jsonPath("$.error").value("Not Found"))
                                .andExpect(jsonPath("$.message").value("Provider not found with id: " + PROVIDER_ID));
        }

        @Test
        @DisplayName("POST /api/v1/providers/me/profile-picture - Should upload profile picture")
        void updateProfilePicture_ShouldReturnUpdatedProvider() throws Exception {
                MockMultipartFile file = new MockMultipartFile(
                                "file",
                                "test.jpg",
                                MediaType.IMAGE_JPEG_VALUE,
                                "test image content".getBytes());

                when(providerService.updateProfilePicture(eq(PROVIDER_ID), any())).thenReturn(providerResponse);

                mockMvc.perform(multipart("/api/v1/providers/me/profile-picture")
                                .file(file)
                                .with(authenticatedProvider())
                                .with(request -> {
                                        request.setMethod("POST");
                                        return request;
                                }))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(PROVIDER_ID));
        }

        @Test
        @DisplayName("GET /api/v1/providers/{id}/schedule - Should return schedule")
        void getSchedule_ShouldReturnSchedule() throws Exception {
                ScheduleResponse scheduleResponse = ScheduleResponse.builder()
                                .workingDays(new ArrayList<>())
                                .timeSlots(new ArrayList<>())
                                .build();

                when(providerService.getSchedule(PROVIDER_ID)).thenReturn(scheduleResponse);

                mockMvc.perform(get("/api/v1/providers/{id}/schedule", PROVIDER_ID)
                                .with(authenticatedConsumer()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.workingDays").isArray());
        }

        @Test
        @DisplayName("POST /api/v1/providers/me/schedule/working-days - Should add working day")
        void addWorkingDay_ShouldReturnSchedule() throws Exception {
                WorkingDayRequest request = WorkingDayRequest.builder()
                                .date(LocalDate.now().plusDays(1))
                                .startTime(LocalTime.of(9, 0))
                                .endTime(LocalTime.of(12, 0))
                                .build();

                ScheduleResponse scheduleResponse = ScheduleResponse.builder()
                                .workingDays(new ArrayList<>())
                                .timeSlots(new ArrayList<>())
                                .build();

                when(providerService.addWorkingDay(eq(PROVIDER_ID), any(WorkingDayRequest.class)))
                                .thenReturn(scheduleResponse);

                mockMvc.perform(post("/api/v1/providers/me/schedule/working-days")
                                .with(authenticatedProvider())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.timeSlots").isArray());
        }

        @Test
        @DisplayName("GET /api/v1/providers/me/time-slots - Should return time slots by date")
        void getTimeSlotsByDate_ShouldReturnList() throws Exception {
                ScheduleResponse.TimeSlotResponse slot = ScheduleResponse.TimeSlotResponse.builder()
                                .id(TIME_SLOT_ID)
                                .date(LocalDate.now().toString())
                                .startTime(LocalTime.of(9, 0))
                                .endTime(LocalTime.of(10, 0))
                                .status("AVAILABLE")
                                .build();

                when(providerService.getTimeSlotsByDate(eq(PROVIDER_ID), any(LocalDate.class)))
                                .thenReturn(List.of(slot));

                mockMvc.perform(get("/api/v1/providers/me/time-slots")
                                .with(authenticatedProvider())
                                .param("date", LocalDate.now().toString()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").value(TIME_SLOT_ID));
        }

        @Test
        @DisplayName("POST /api/v1/providers/me/documents - Should upload document")
        void uploadDocument_ShouldReturnDocument() throws Exception {
                MockMultipartFile file = new MockMultipartFile(
                                "file",
                                "doc.pdf",
                                MediaType.APPLICATION_PDF_VALUE,
                                "test doc content".getBytes());

                DocumentResponse documentResponse = DocumentResponse.builder()
                                .id(DOCUMENT_ID)
                                .title("doc.pdf")
                                .type("LICENSE")
                                .filePath("doc-url")
                                .build();

                when(providerService.uploadDocument(eq(PROVIDER_ID), any(), eq("LICENSE")))
                                .thenReturn(documentResponse);

                mockMvc.perform(multipart("/api/v1/providers/me/documents")
                                .file(file)
                                .with(authenticatedProvider())
                                .param("documentType", "LICENSE"))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").value(DOCUMENT_ID));
        }

        @Test
        @DisplayName("GET /api/v1/providers/{id}/documents - Should return documents")
        void getProviderDocuments_ShouldReturnList() throws Exception {
                DocumentResponse documentResponse = DocumentResponse.builder()
                                .id(DOCUMENT_ID)
                                .title("doc.pdf")
                                .type("LICENSE")
                                .filePath("doc-url")
                                .build();

                when(providerService.getProviderDocuments(PROVIDER_ID)).thenReturn(List.of(documentResponse));

                mockMvc.perform(get("/api/v1/providers/{id}/documents", PROVIDER_ID)
                                .with(authenticatedConsumer()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").value(DOCUMENT_ID));
        }

        @Test
        @DisplayName("DELETE /api/v1/providers/me/documents/{documentId} - Should delete document")
        void deleteDocument_ShouldReturnSuccess() throws Exception {
                when(providerService.deleteDocument(PROVIDER_ID, DOCUMENT_ID)).thenReturn(profileResponse);

                mockMvc.perform(delete("/api/v1/providers/me/documents/{documentId}", DOCUMENT_ID)
                                .with(authenticatedProvider()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("GET /api/v1/providers/all - Should return providers")
        void allProviders_ShouldReturnList() throws Exception {
                List<ProviderSummaryResponse> providers = List.of(
                                TestDataFactory.createProviderSummaryResponse(1L),
                                TestDataFactory.createProviderSummaryResponse(2L));
                when(providerService.allProviders()).thenReturn(providers);

                mockMvc.perform(get("/api/v1/providers/all"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        @DisplayName("GET /api/v1/providers/{id}/verification-status - Should return status")
        void getVerificationStatus_ShouldReturnStatus() throws Exception {
                when(providerService.getVerificationStatus(PROVIDER_ID)).thenReturn(VerificationStatus.VERIFIED);

                mockMvc.perform(get("/api/v1/providers/{id}/verification-status", PROVIDER_ID))
                                .andExpect(status().isOk())
                                .andExpect(content().string("\"VERIFIED\""));
        }

        @Test
        @DisplayName("GET /api/v1/providers/search - keyword filter")
        void search_keyword() throws Exception {
                when(providerService.search(
                                eq("drain"),
                                isNull(),
                                isNull(),
                                eq(CONSUMER_ID),
                                eq(5.0),
                                eq("rating"),
                                any(Pageable.class))).thenReturn(new PageImpl<>(new ArrayList<>()));

                mockMvc.perform(get("/api/v1/providers/search")
                                .param("consumerId", CONSUMER_ID.toString())
                                .param("keyword", "drain")
                                .param("page", "0")
                                .param("size", "10"))
                                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /api/v1/providers/search - category id")
        void search_category_id() throws Exception {
                when(providerService.search(
                                isNull(),
                                eq(3L),
                                isNull(),
                                eq(CONSUMER_ID),
                                eq(5.0),
                                eq("rating"),
                                any(Pageable.class))).thenReturn(new PageImpl<>(new ArrayList<>()));

                mockMvc.perform(get("/api/v1/providers/search")
                                .param("consumerId", CONSUMER_ID.toString())
                                .param("categoryId", "3")
                                .param("page", "0")
                                .param("size", "10"))
                                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET /api/v1/providers/search - category name")
        void search_category_name() throws Exception {
                when(providerService.search(
                                isNull(),
                                isNull(),
                                eq("Plumbing"),
                                eq(CONSUMER_ID),
                                eq(5.0),
                                eq("rating"),
                                any(Pageable.class))).thenReturn(new PageImpl<>(new ArrayList<>()));

                mockMvc.perform(get("/api/v1/providers/search")
                                .param("consumerId", CONSUMER_ID.toString())
                                .param("categoryName", "Plumbing")
                                .param("page", "0")
                                .param("size", "10"))
                                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET search - location radius")
        void search_location() throws Exception {

                SearchResponse response = SearchResponse.builder()
                        .id(1L)
                        .name("Ibrahim Nasser")
                        .serviceType("Drain Cleaning")
                        .serviceTypeAr("تنظيف المصارف")
                        .categoryName("Plumbing")
                        .averageRating(4.8)
                        .completedJobs(50)
                        .price(150.0)
                        .estimatedArrivalTime(15)
                        .area("Nasr City")
                        .serviceAreaRadius(10.0)
                        .build();

                response.setDistance(0.39);

                Page<SearchResponse> page =
                        new PageImpl<>(List.of(response), PageRequest.of(0, 10), 1);

                when(providerService.search(
                        isNull(),
                        isNull(),
                        isNull(),
                        eq(1L),
                        eq(10.0),
                        eq("distance"),
                        any(Pageable.class)))
                        .thenReturn(page);

                mockMvc.perform(get("/api/v1/providers/search")
                                .param("consumerId", "1")
                                .param("radius", "10")
                                .param("sortBy", "distance")
                                .param("page", "0")
                                .param("size", "10"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content[0].formattedDistance").value("390 m"))
                        .andExpect(jsonPath("$.content[0].estimatedArrivalTime").value(15));
        }

        @Test
        @DisplayName("GET /api/v1/providers/search - sort by price")
        void search_sort_price() throws Exception {
                when(providerService.search(
                                isNull(),
                                isNull(),
                                isNull(),
                                eq(CONSUMER_ID),
                                eq(5.0),
                                eq("price_low"),
                                any(Pageable.class))).thenReturn(new PageImpl<>(new ArrayList<>()));

                mockMvc.perform(get("/api/v1/providers/search")
                                .param("consumerId", CONSUMER_ID.toString())
                                .param("sortBy", "price_low")
                                .param("page", "0")
                                .param("size", "10"))
                                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("GET search - all filters combined")
        void search_all_filters() throws Exception {

                SearchResponse response = SearchResponse.builder()

                        .id(1L)

                        .name("Ibrahim Nasser")

                        .serviceType("Drain Cleaning")

                        .serviceTypeAr("تنظيف المصارف")

                        .categoryName("Plumbing")

                        .averageRating(4.8)

                        .completedJobs(50)

                        .price(150.0)

                        .estimatedArrivalTime(15)

                        .area("Nasr City")

                        .serviceAreaRadius(10.0)

                        .build();



                response.setDistance(0.39);



                Page<SearchResponse> page =

                        new PageImpl<>(List.of(response), PageRequest.of(0, 10), 1);



                when(providerService.search(

                        eq("drain"),

                        eq(3L),

                        eq("Plumbing"),

                        eq(1L),

                        eq(10.0),

                        eq("distance"),

                        any(Pageable.class)))

                        .thenReturn(page);



                mockMvc.perform(get("/api/v1/providers/search")

                                .param("consumerId", "1")

                                .param("keyword", "drain")

                                .param("categoryId", "3")

                                .param("categoryName", "Plumbing")

                                .param("radius", "10")

                                .param("sortBy", "distance")

                                .param("page", "0")

                                .param("size", "10"))

                        .andExpect(status().isOk())

                        .andExpect(jsonPath("$.content[0].categoryName").value("Plumbing"))

                        .andExpect(jsonPath("$.content[0].serviceType").value("Drain Cleaning"))

                        .andExpect(jsonPath("$.content[0].formattedDistance").value("390 m"));

        }

        @Test
        @DisplayName("GET /api/v1/providers/top-rated-near-me")
        void topRatedNearMe() throws Exception {

                SearchResponse response = SearchResponse.builder()
                        .id(1L)
                        .name("Ibrahim Nasser")
                        .serviceType("Drain Cleaning")
                        .serviceTypeAr("تنظيف المصارف")
                        .categoryName("Plumbing")
                        .averageRating(4.8)
                        .completedJobs(50)
                        .price(150.0)
                        .estimatedArrivalTime(15)
                        .area("Nasr City")
                        .serviceAreaRadius(10.0)
                        .build();

                response.setDistance(0.39);

                Page<SearchResponse> page =
                        new PageImpl<>(List.of(response), PageRequest.of(0, 10), 1);

                when(providerService.topRatedNearMe(
                        eq(CONSUMER_ID),
                        eq(10.0),
                        any(Pageable.class)))
                        .thenReturn(page);

                mockMvc.perform(get("/api/v1/providers/top-rated-near-me")
                                .with(authenticatedConsumer())
                                .param("consumerId", CONSUMER_ID.toString())
                                .param("radius", "10")
                                .param("page", "0")
                                .param("size", "10"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.content[0].id").value(1))
                        .andExpect(jsonPath("$.content[0].name").value("Ibrahim Nasser"))
                        .andExpect(jsonPath("$.content[0].categoryName").value("Plumbing"))
                        .andExpect(jsonPath("$.content[0].serviceType").value("Drain Cleaning"))
                        .andExpect(jsonPath("$.content[0].averageRating").value(4.8))
                        .andExpect(jsonPath("$.content[0].price").value(150.0))
                        .andExpect(jsonPath("$.content[0].formattedDistance").value("390 m"));
        }
}