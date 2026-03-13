package com.aykhedma.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CancelBookingRequest Validation Tests")
class CancelBookingDtoTest
{
    private Validator validator;

    @BeforeEach
    public void setUp()
    {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory())
        {
            validator = factory.getValidator();
        }
    }

    @Nested
    @DisplayName("Valid Data Tests")
    class ValidDataTest
    {
        @Test
        @DisplayName("All Fields Valid")
        void allFieldsValidTest()
        {
            CancelBookingRequest request = CancelBookingRequest.builder()
                    .bookingId(15L)
                    .cancellationReason("Going for a trip now")
                    .build();

            Set<ConstraintViolation<CancelBookingRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("Booking ID Validation Tests")
    class BookingIdValidationTest
    {
        @Test
        @DisplayName("BookingId Field Missing")
        void noBookingIdFieldViolationTest()
        {
            CancelBookingRequest request = CancelBookingRequest.builder()
                    .cancellationReason("Going for a trip now")
                    .build();

            Set<ConstraintViolation<CancelBookingRequest>> violations = validator.validate(request);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getMessage().contains("Booking ID is required"));
        }

        @Test
        @DisplayName("Negative BookingId Field")
        void negativeBookingIdFieldViolationTest()
        {
            CancelBookingRequest request = CancelBookingRequest.builder()
                    .bookingId(-15L)
                    .cancellationReason("Going for a trip now")
                    .build();

            Set<ConstraintViolation<CancelBookingRequest>> violations = validator.validate(request);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getMessage().contains("Booking ID must be positive"));
        }
    }

    @Nested
    @DisplayName("Cancellation Reason Validation Tests")
    class CancellationReasonValidationTest
    {
        @Test
        @DisplayName("CancellationReason Field Missing")
        void noCancellationReasonViolationTest()
        {
            CancelBookingRequest request = CancelBookingRequest.builder()
                    .bookingId(15L)
                    .build();

            Set<ConstraintViolation<CancelBookingRequest>> violations = validator.validate(request);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getMessage().contains("Cancellation reason is required"));
        }

        @Test
        @DisplayName("CancellationReason Field Exceeds 200 Characters")
        void cancellationReasonTooLongViolationTest()
        {
            String longReason = "bla".repeat(100);

            CancelBookingRequest request = CancelBookingRequest.builder()
                    .bookingId(15L)
                    .cancellationReason(longReason)
                    .build();

            Set<ConstraintViolation<CancelBookingRequest>> violations = validator.validate(request);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getMessage().contains("Cancellation reason cannot exceed 200 characters"));
        }
    }
}
