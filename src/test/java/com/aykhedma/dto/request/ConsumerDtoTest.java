package com.aykhedma.dto.request;

import com.aykhedma.dto.location.LocationDTO;
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

@DisplayName("ConsumerProfileRequest Validation Tests")
class ConsumerDtoTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Nested
    @DisplayName("Valid Data Tests")
    class ValidDataTests {

        @Test
        @DisplayName("Should pass validation with all fields valid")
        void allFieldsValid_NoViolations() {
            // Arrange
            ConsumerProfileRequest request = ConsumerProfileRequest.builder()
                    .name("John Doe")
                    .email("john.doe@example.com")
                    .phoneNumber("01234567890")
                    .preferredLanguage("en")
                    .location(LocationDTO.builder()
                            .latitude(30.0444)
                            .longitude(31.2357)
                            .address("123 Street")
                            .area("Maadi")
                            .city("Cairo")
                            .build())
                    .build();

            // Act
            Set<ConstraintViolation<ConsumerProfileRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should pass validation with only name field")
        void onlyNameField_NoViolations() {
            // Arrange
            ConsumerProfileRequest request = ConsumerProfileRequest.builder()
                    .name("John Doe")
                    .build();

            // Act
            Set<ConstraintViolation<ConsumerProfileRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("Email Validation Tests")
    class EmailValidationTests {

        @Test
        @DisplayName("Should fail when email is invalid - missing @")
        void invalidEmail_missingAt_hasViolation() {
            // Arrange
            ConsumerProfileRequest request = ConsumerProfileRequest.builder()
                    .email("invalid-email")
                    .build();

            // Act
            Set<ConstraintViolation<ConsumerProfileRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getMessage().contains("Invalid email format"));
        }

        @Test
        @DisplayName("Should pass when email is valid")
        void validEmail_noViolations() {
            // Arrange
            ConsumerProfileRequest request = ConsumerProfileRequest.builder()
                    .email("valid.email@example.com")
                    .build();

            // Act
            Set<ConstraintViolation<ConsumerProfileRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("Phone Number Validation Tests")
    class PhoneNumberValidationTests {

        @Test
        @DisplayName("Should fail when phone number is too short")
        void phoneNumber_tooShort_hasViolation() {
            // Arrange
            ConsumerProfileRequest request = ConsumerProfileRequest.builder()
                    .phoneNumber("0123456") // 7 digits, should be 11
                    .build();

            // Act
            Set<ConstraintViolation<ConsumerProfileRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getMessage().contains("valid Egyptian number"));
        }

        @Test
        @DisplayName("Should fail when phone number doesn't start with 01")
        void phoneNumber_wrongPrefix_hasViolation() {
            // Arrange
            ConsumerProfileRequest request = ConsumerProfileRequest.builder()
                    .phoneNumber("02234567890") // Starts with 02, not 01
                    .build();

            // Act
            Set<ConstraintViolation<ConsumerProfileRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getMessage().contains("valid Egyptian number"));
        }

        @Test
        @DisplayName("Should pass when phone number is valid Egyptian number")
        void validEgyptianPhoneNumber_noViolations() {
            // Arrange
            ConsumerProfileRequest request = ConsumerProfileRequest.builder()
                    .phoneNumber("01234567890")
                    .build();

            // Act
            Set<ConstraintViolation<ConsumerProfileRequest>> violations = validator.validate(request);

            // Assert
            assertThat(violations).isEmpty();
        }
    }
}