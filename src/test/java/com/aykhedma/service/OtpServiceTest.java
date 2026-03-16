package com.aykhedma.service;

import com.aykhedma.auth.OtpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OtpService Unit Tests")
class OtpServiceTest {

    private OtpService otpService;

    @Mock
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        otpService = new OtpService(notificationService);
    }

    @Test
    @DisplayName("generateOtp() + validateOtp() should return true for correct OTP")
    void generateAndValidate_correctOtp_returnsTrue() {
        String email = "test@mail.com";
        String generatedOtp = otpService.generateOtp(email);

        boolean result = otpService.validateOtp(email, generatedOtp);

        assertThat(result).isTrue();
        verify(notificationService).sendOtpEmail(email, generatedOtp);
    }

    @Test
    @DisplayName("validateOtp() should return false for wrong OTP code")
    void validateOtp_wrongCode_returnsFalse() {
        otpService.generateOtp("test@mail.com");

        boolean result = otpService.validateOtp("test@mail.com", "wrong!");
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("validateOtp() should return false for non-existent email")
    void validateOtp_nonExistentEmail_returnsFalse() {
        boolean result = otpService.validateOtp("missing@mail.com", "123456");
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Generated OTP should be a 6-digit number and validate correctly")
    void generateOtp_producesValidSixDigitOtp() {
        String email = "test2@mail.com";
        String generatedOtp = otpService.generateOtp(email);

        assertThat(generatedOtp).matches("\\d{6}");
        assertThat(otpService.validateOtp(email, generatedOtp)).isTrue();
    }

    @Test
    @DisplayName("Validated OTP should not be reusable")
    void validateOtp_reuseOtp_returnsFalseOnSecondTry() {
        String email = "test3@mail.com";
        String generatedOtp = otpService.generateOtp(email);

        assertThat(otpService.validateOtp(email, generatedOtp)).isTrue();
        assertThat(otpService.validateOtp(email, generatedOtp)).isFalse();
    }
}
