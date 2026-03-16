package com.aykhedma.controller;

import com.aykhedma.config.TestSecurityConfig;
import com.aykhedma.dto.request.ProviderProfileRequest;
import com.aykhedma.dto.request.WorkingDayRequest;
import com.aykhedma.dto.response.DocumentResponse;
import com.aykhedma.dto.response.ProfileResponse;
import com.aykhedma.dto.response.ProviderResponse;
import com.aykhedma.dto.response.ProviderSummaryResponse;
import com.aykhedma.dto.response.ScheduleResponse;
import com.aykhedma.exception.GlobalExceptionHandler;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.model.user.VerificationStatus;
import com.aykhedma.security.CustomUserDetailsService;
import com.aykhedma.security.JwtService;
import com.aykhedma.service.ProviderService;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProviderController.class)
@Import({ TestSecurityConfig.class, GlobalExceptionHandler.class })
@DisplayName("Provider Controller Unit Tests")
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

        @MockBean
        private JwtService jwtService;

        @MockBean
        private CustomUserDetailsService customUserDetailsService;

        private ProviderResponse providerResponse;
        private ProviderProfileRequest profileRequest;
        private ProfileResponse profileResponse;

        private final Long PROVIDER_ID = 1L;
        private final Long TIME_SLOT_ID = 10L;
        private final Long DOCUMENT_ID = 20L;

        private RequestPostProcessor authenticatedProvider() {
                var principal = new PrincipalPayload(PROVIDER_ID);
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_PROVIDER"));
                var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                return SecurityMockMvcRequestPostProcessors.authentication(auth);
        }

        @BeforeEach
        void setUp() {
                providerResponse = ProviderResponse.builder()
                                .id(PROVIDER_ID)
                                .name("Test Provider")
                                .email("provider@example.com")
                                .build();

                profileRequest = ProviderProfileRequest.builder()
                                .name("Updated Provider")
                                .email("updated@example.com")
                                .build();

                profileResponse = ProfileResponse.builder()
                                .success(true)
                                .message("Success")
                                .build();
        }

        @Test
        @DisplayName("GET /api/providers/{id} - Should return provider profile")
        void getProviderProfile_ShouldReturnProvider() throws Exception {
                when(providerService.getProviderProfile(PROVIDER_ID)).thenReturn(providerResponse);

                mockMvc.perform(get("/api/v1/providers/{id}", PROVIDER_ID))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                                .andExpect(jsonPath("$.id").value(PROVIDER_ID))
                                .andExpect(jsonPath("$.name").value(providerResponse.getName()));
        }

        @Test
        @DisplayName("GET /api/providers/{id} - Should return 404 when provider not found")
        void getProviderProfile_NotFound_Returns404() throws Exception {
                when(providerService.getProviderProfile(PROVIDER_ID))
                                .thenThrow(new ResourceNotFoundException("Provider not found with id: " + PROVIDER_ID));

                mockMvc.perform(get("/api/v1/providers/{id}", PROVIDER_ID))
                                .andExpect(status().isNotFound())
                                .andExpect(jsonPath("$.status").value(404))
                                .andExpect(jsonPath("$.error").value("Not Found"))
                                .andExpect(jsonPath("$.message").value("Provider not found with id: " + PROVIDER_ID));
        }

        @Test
        @DisplayName("PUT /api/providers/{id} - Should update provider profile")
        void updateProviderProfile_ShouldReturnUpdatedProvider() throws Exception {
                when(providerService.updateProviderProfile(eq(PROVIDER_ID), any(ProviderProfileRequest.class)))
                                .thenReturn(providerResponse);

                mockMvc.perform(put("/api/v1/providers/me")
                                .with(authenticatedProvider())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(profileRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value(PROVIDER_ID));
        }

        @Test
        @DisplayName("PUT /api/providers/{id} - Should return 404 when provider not found")
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
        @DisplayName("POST /api/providers/{id}/profile-picture - Should upload profile picture")
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
        @DisplayName("GET /api/providers/{id}/schedule - Should return schedule")
        void getSchedule_ShouldReturnSchedule() throws Exception {
                ScheduleResponse scheduleResponse = ScheduleResponse.builder()
                                .workingDays(List.of())
                                .timeSlots(List.of())
                                .build();

                when(providerService.getSchedule(PROVIDER_ID)).thenReturn(scheduleResponse);

                mockMvc.perform(get("/api/v1/providers/{id}/schedule", PROVIDER_ID))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.workingDays").isArray());
        }

        @Test
        @DisplayName("POST /api/providers/{id}/schedule/working-days - Should add working day")
        void addWorkingDay_ShouldReturnSchedule() throws Exception {
                WorkingDayRequest request = WorkingDayRequest.builder()
                                .date(LocalDate.now().plusDays(1))
                                .startTime(LocalTime.of(9, 0))
                                .endTime(LocalTime.of(12, 0))
                                .build();

                ScheduleResponse scheduleResponse = ScheduleResponse.builder()
                                .workingDays(List.of())
                                .timeSlots(List.of())
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
        @DisplayName("GET /api/providers/{id}/time-slots - Should return time slots by date")
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

        /*
         * bookTimeSlot_ShouldReturnBookedSlot is commented out because
         * bookTimeSlot(...)
         * endpoint/service method has been removed from current API.
         */
        // @Test
        // @DisplayName("POST /api/providers/{id}/time-slots/book - Should book time
        // slot")
        // void bookTimeSlot_ShouldReturnBookedSlot() throws Exception {
        // ScheduleResponse.TimeSlotResponse slot =
        // ScheduleResponse.TimeSlotResponse.builder()
        // .id(TIME_SLOT_ID)
        // .date(LocalDate.now().toString())
        // .startTime(LocalTime.of(9, 0))
        // .endTime(LocalTime.of(10, 0))
        // .status("BOOKED")
        // .build();
        //
        // when(providerService.bookTimeSlot(eq(PROVIDER_ID), any(LocalDate.class),
        // any(LocalTime.class), eq(60)))
        // .thenReturn(slot);
        //
        // mockMvc.perform(post("/api/v1/providers/{id}/time-slots/book", PROVIDER_ID)
        // .param("date", LocalDate.now().toString())
        // .param("startTime", "09:00")
        // .param("durationMinutes", "60"))
        // .andExpect(status().isOk())
        // .andExpect(jsonPath("$.id").value(TIME_SLOT_ID));
        // }

        @Test
        @DisplayName("POST /api/providers/{id}/documents - Should upload document")
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
        @DisplayName("GET /api/providers/{id}/documents - Should return documents")
        void getProviderDocuments_ShouldReturnList() throws Exception {
                DocumentResponse documentResponse = DocumentResponse.builder()
                                .id(DOCUMENT_ID)
                                .title("doc.pdf")
                                .type("LICENSE")
                                .filePath("doc-url")
                                .build();

                when(providerService.getProviderDocuments(PROVIDER_ID)).thenReturn(List.of(documentResponse));

                mockMvc.perform(get("/api/v1/providers/{id}/documents", PROVIDER_ID))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].id").value(DOCUMENT_ID));
        }

        @Test
        @DisplayName("DELETE /api/providers/{id}/documents/{documentId} - Should delete document")
        void deleteDocument_ShouldReturnSuccess() throws Exception {
                when(providerService.deleteDocument(PROVIDER_ID, DOCUMENT_ID)).thenReturn(profileResponse);

                mockMvc.perform(delete("/api/v1/providers/me/documents/{documentId}", DOCUMENT_ID)
                                .with(authenticatedProvider()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("GET /api/providers/all - Should return providers")
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
        @DisplayName("GET /api/providers/{id}/verification-status - Should return status")
        void getVerificationStatus_ShouldReturnStatus() throws Exception {
                when(providerService.getVerificationStatus(PROVIDER_ID)).thenReturn(VerificationStatus.VERIFIED);

                mockMvc.perform(get("/api/v1/providers/{id}/verification-status", PROVIDER_ID))
                                .andExpect(status().isOk())
                                .andExpect(content().string("\"VERIFIED\""));
        }
}
