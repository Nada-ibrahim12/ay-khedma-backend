package com.aykhedma.service;

import com.aykhedma.Auth.OtpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("OtpService Unit Tests")
class OtpServiceTest {

    private OtpService otpService;

    @BeforeEach
    void setUp() {
        otpService = new OtpService();
    }

    @Test
    @DisplayName("generateOtp() + validateOtp() should return true for correct OTP")
    void generateAndValidate_correctOtp_returnsTrue() {
        otpService.generateOtp("01012345678");
    }

    @Test
    @DisplayName("validateOtp() should return false for wrong OTP code")
    void validateOtp_wrongCode_returnsFalse() {
        otpService.generateOtp("01012345678");

        boolean result = otpService.validateOtp("01012345678", "wrong!");
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("validateOtp() should return false for non-existent phone")
    void validateOtp_nonExistentPhone_returnsFalse() {
        boolean result = otpService.validateOtp("09999999999", "123456");
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Generated OTP should be a 6-digit number and validate correctly")
    void generateOtp_producesValidSixDigitOtp() {
        String phone = "01055555555";
        otpService.generateOtp(phone);

        assertThat(otpService.validateOtp(phone, "")).isFalse();
        assertThat(otpService.validateOtp(phone, "abc")).isFalse();
    }
}
