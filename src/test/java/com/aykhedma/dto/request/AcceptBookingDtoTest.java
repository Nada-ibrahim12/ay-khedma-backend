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

@DisplayName("AcceptBookingRequest Validation Tests")
class AcceptBookingDtoTest
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
            AcceptBookingRequest request = AcceptBookingRequest.builder()
                    .bookingId(12L)
                    .estimatedDuration(120L)
                    .overrideWorkingHours(true)
                    .build();

            Set<ConstraintViolation<AcceptBookingRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("No OverrideWorkingHours Field")
        void noOverrideWorkingHoursFieldValidTest()
        {
            AcceptBookingRequest request = AcceptBookingRequest.builder()
                    .bookingId(12L)
                    .estimatedDuration(120L)
                    .build();

            Set<ConstraintViolation<AcceptBookingRequest>> violations = validator.validate(request);

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
            AcceptBookingRequest request = AcceptBookingRequest.builder()
                    .estimatedDuration(120L)
                    .overrideWorkingHours(true)
                    .build();

            Set<ConstraintViolation<AcceptBookingRequest>> violations = validator.validate(request);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getMessage().contains("Booking ID is required"));
        }

        @Test
        @DisplayName("Negative BookingId Field")
        void negativeBookingIdFieldViolationTest()
        {
            AcceptBookingRequest request = AcceptBookingRequest.builder()
                    .bookingId(-12L)
                    .estimatedDuration(120L)
                    .overrideWorkingHours(true)
                    .build();

            Set<ConstraintViolation<AcceptBookingRequest>> violations = validator.validate(request);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getMessage().contains("Booking ID must be positive"));
        }
    }

    @Nested
    @DisplayName("Estimated Duration Validation Tests")
    class  EstimatedDurationValidationTest
    {
        @Test
        @DisplayName("EstimatedDuration Field Missing")
        void noEstimatedDurationViolationTest()
        {
            AcceptBookingRequest request = AcceptBookingRequest.builder()
                    .bookingId(12L)
                    .overrideWorkingHours(true)
                    .build();

            Set<ConstraintViolation<AcceptBookingRequest>> violations = validator.validate(request);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getMessage().contains("Estimated duration is required"));
        }

        @Test
        @DisplayName("EstimatedDuration Field Less Than 30")
        void lessThan30EstimatedDurationFieldViolationTest()
        {
            AcceptBookingRequest request = AcceptBookingRequest.builder()
                    .bookingId(12L)
                    .estimatedDuration(-120L)
                    .overrideWorkingHours(true)
                    .build();

            Set<ConstraintViolation<AcceptBookingRequest>> violations = validator.validate(request);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getMessage().contains("Estimated duration cannot be less than 30 minutes"));
        }
    }
}
