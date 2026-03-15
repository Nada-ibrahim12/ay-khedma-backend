package com.aykhedma.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BookingRequest Validation Tests")
class BookingDtoTest
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
            BookingRequest request = BookingRequest.builder()
                    .providerId(15L)
                    .requestedDate(LocalDate.now().plusDays(1))
                    .requestedTime(LocalTime.now())
                    .problemDescription("Need help ASAP")
                    .build();

            Set<ConstraintViolation<BookingRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("Provider ID Validation Tests")
    class ProviderIdValidationTest
    {
        @Test
        @DisplayName("ProviderId Field Missing")
        void noProviderIdFieldViolationTest()
        {
            BookingRequest request = BookingRequest.builder()
                    .requestedDate(LocalDate.now().plusDays(1))
                    .requestedTime(LocalTime.now())
                    .problemDescription("Need help ASAP")
                    .build();

            Set<ConstraintViolation<BookingRequest>> violations = validator.validate(request);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getMessage().contains("Provider ID is required"));
        }

        @Test
        @DisplayName("Negative ProviderId Field")
        void negativeProviderIdFieldViolationTest()
        {
            BookingRequest request = BookingRequest.builder()
                    .providerId(-15L)
                    .requestedDate(LocalDate.now().plusDays(1))
                    .requestedTime(LocalTime.now())
                    .problemDescription("Need help ASAP")
                    .build();

            Set<ConstraintViolation<BookingRequest>> violations = validator.validate(request);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getMessage().contains("Provider ID must be positive"));
        }
    }

    @Nested
    @DisplayName("Requested Date Validation Tests")
    class RequestedDateValidationTest
    {
        @Test
        @DisplayName("RequestedDate Field Missing")
        void noRequestedDateViolationTest()
        {
            BookingRequest request = BookingRequest.builder()
                    .providerId(15L)
                    .requestedTime(LocalTime.now())
                    .problemDescription("Need help ASAP")
                    .build();

            Set<ConstraintViolation<BookingRequest>> violations = validator.validate(request);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getMessage().contains("Requested date is required"));
        }

        @Test
        @DisplayName("RequestedDate Field In The Past")
        void requestedDateInPastViolationTest()
        {
            BookingRequest request = BookingRequest.builder()
                    .providerId(10L)
                    .requestedDate(LocalDate.now().minusDays(1))
                    .requestedTime(LocalTime.now())
                    .problemDescription("Need help ASAP")
                    .build();

            Set<ConstraintViolation<BookingRequest>> violations = validator.validate(request);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getMessage().contains("Requested date must be today or in the future"));
        }
    }

    @Nested
    @DisplayName("Requested Time Validation Tests")
    class RequestedTimeValidationTest
    {
        @Test
        @DisplayName("RequestedTime Field Missing")
        void noRequestedTimeViolationTest()
        {
            BookingRequest request = BookingRequest.builder()
                    .providerId(15L)
                    .requestedDate(LocalDate.now().plusDays(1))
                    .problemDescription("Need help ASAP")
                    .build();

            Set<ConstraintViolation<BookingRequest>> violations = validator.validate(request);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getMessage().contains("Requested time is required"));
        }
    }

    @Nested
    @DisplayName("Problem Description Validation Tests")
    class ProblemDescriptionValidationTest
    {
        @Test
        @DisplayName("ProblemDescription Field Missing")
        void noProblemDescriptionViolationTest()
        {
            BookingRequest request = BookingRequest.builder()
                    .providerId(15L)
                    .requestedDate(LocalDate.now().plusDays(1))
                    .requestedTime(LocalTime.now())
                    .build();

            Set<ConstraintViolation<BookingRequest>> violations = validator.validate(request);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getMessage().contains("Problem description is required"));
        }

        @Test
        @DisplayName("ProblemDescription Field Exceeds 1000 Characters")
        void problemDescriptionTooLongViolationTest()
        {
            String longDescription = "bla".repeat(350);

            BookingRequest request = BookingRequest.builder()
                    .providerId(10L)
                    .requestedDate(LocalDate.now().plusDays(1))
                    .requestedTime(LocalTime.now())
                    .problemDescription(longDescription)
                    .build();

            Set<ConstraintViolation<BookingRequest>> violations = validator.validate(request);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getMessage().contains("Problem description cannot exceed 1000 characters"));
        }
    }
}
