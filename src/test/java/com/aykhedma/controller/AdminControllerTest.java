package com.aykhedma.controller;

import com.aykhedma.auth.AuthService;
import com.aykhedma.dto.request.RejectProviderRequest;
import com.aykhedma.dto.response.ProviderResponse;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.model.user.VerificationStatus;
import com.aykhedma.service.AdminService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminController Unit Tests")
class AdminControllerTest {

    @Mock
    private AdminService adminService;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AdminController adminController;

    @Test
    @DisplayName("rejectProvider should accept reason from request body")
    void rejectProvider_acceptsBodyReason() {
        Long providerId = 12L;
        String reason = "National ID image is not readable";
        ProviderResponse providerResponse = rejectedResponse(providerId, reason);

        when(adminService.rejectProvider(providerId, reason)).thenReturn(providerResponse);

        ResponseEntity<ProviderResponse> response = adminController.rejectProvider(
                providerId,
                RejectProviderRequest.builder().reason(reason).build(),
                null);

        assertThat(response.getBody()).isEqualTo(providerResponse);
        verify(adminService).rejectProvider(providerId, reason);
    }

    @Test
    @DisplayName("rejectProvider should accept reason from query parameter")
    void rejectProvider_acceptsQueryReason() {
        Long providerId = 13L;
        String reason = "Required certificates were not uploaded";
        ProviderResponse providerResponse = rejectedResponse(providerId, reason);

        when(adminService.rejectProvider(providerId, reason)).thenReturn(providerResponse);

        ResponseEntity<ProviderResponse> response = adminController.rejectProvider(providerId, null, reason);

        assertThat(response.getBody()).isEqualTo(providerResponse);
        verify(adminService).rejectProvider(providerId, reason);
    }

    @Test
    @DisplayName("RejectProviderRequest should accept rejectionReason JSON alias")
    void rejectProviderRequest_acceptsRejectionReasonAlias() throws Exception {
        RejectProviderRequest request = new ObjectMapper().readValue(
                "{\"rejectionReason\":\"National ID image is not readable\"}",
                RejectProviderRequest.class);

        assertThat(request.getReason()).isEqualTo("National ID image is not readable");
    }

    @Test
    @DisplayName("rejectProvider should reject too-short reasons before service call")
    void rejectProvider_rejectsInvalidReason() {
        assertThatThrownBy(() -> adminController.rejectProvider(
                14L,
                RejectProviderRequest.builder().reason("short").build(),
                null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Reason must be between 10 and 500 characters");

        verifyNoInteractions(adminService);
    }

    private ProviderResponse rejectedResponse(Long providerId, String reason) {
        return ProviderResponse.builder()
                .id(providerId)
                .verificationStatus(VerificationStatus.REJECTED)
                .rejectionReason(reason)
                .build();
    }
}
