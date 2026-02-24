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

@DisplayName("ProviderProfileRequest Validation Tests")
class ProviderDtoTest {

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
            ProviderProfileRequest request = ProviderProfileRequest.builder()
                    .name("John Doe")
                    .email("john.doe@example.com")
                    .phoneNumber("01234567890")
                    .preferredLanguage("en")
                    .bio("Experienced provider")
                    .serviceTypeId(1L)
                    .price(100.0)
                    .priceType("HOUR")
                    .emergencyEnabled(true)
                    .serviceArea("Maadi")
                    .serviceAreaRadius(5.0)
                    .build();

            Set<ConstraintViolation<ProviderProfileRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("Should pass validation with only name field")
        void onlyNameField_NoViolations() {
            ProviderProfileRequest request = ProviderProfileRequest.builder()
                    .name("John Doe")
                    .build();

            Set<ConstraintViolation<ProviderProfileRequest>> violations = validator.validate(request);

            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("Email Validation Tests")
    class EmailValidationTests {

        @Test
        @DisplayName("Should fail when email is invalid")
        void invalidEmail_HasViolation() {
            ProviderProfileRequest request = ProviderProfileRequest.builder()
                    .email("invalid-email")
                    .build();

            Set<ConstraintViolation<ProviderProfileRequest>> violations = validator.validate(request);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getMessage().contains("Invalid email format"));
        }
    }

    @Nested
    @DisplayName("Phone Number Validation Tests")
    class PhoneNumberValidationTests {

        @Test
        @DisplayName("Should fail when phone number is invalid")
        void invalidPhoneNumber_HasViolation() {
            ProviderProfileRequest request = ProviderProfileRequest.builder()
                    .phoneNumber("02123456789")
                    .build();

            Set<ConstraintViolation<ProviderProfileRequest>> violations = validator.validate(request);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getMessage().contains("valid Egyptian number"));
        }
    }

    @Nested
    @DisplayName("Price Validation Tests")
    class PriceValidationTests {

        @Test
        @DisplayName("Should fail when price is not positive")
        void invalidPrice_HasViolation() {
            ProviderProfileRequest request = ProviderProfileRequest.builder()
                    .price(0.0)
                    .build();

            Set<ConstraintViolation<ProviderProfileRequest>> violations = validator.validate(request);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getMessage().contains("greater than 0"));
        }

        @Test
        @DisplayName("Should fail when price type is invalid")
        void invalidPriceType_HasViolation() {
            ProviderProfileRequest request = ProviderProfileRequest.builder()
                    .priceType("DAY")
                    .build();

            Set<ConstraintViolation<ProviderProfileRequest>> violations = validator.validate(request);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getMessage().contains("Price type"));
        }
    }

    @Nested
    @DisplayName("Name Validation Tests")
    class NameValidationTests {

        @Test
        @DisplayName("Should fail when name contains invalid characters")
        void invalidName_HasViolation() {
            ProviderProfileRequest request = ProviderProfileRequest.builder()
                    .name("John123")
                    .build();

            Set<ConstraintViolation<ProviderProfileRequest>> violations = validator.validate(request);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getMessage().contains("only contain letters"));
        }
    }

    @Nested
    @DisplayName("Language Validation Tests")
    class LanguageValidationTests {

        @Test
        @DisplayName("Should fail when preferred language is invalid")
        void invalidLanguage_HasViolation() {
            ProviderProfileRequest request = ProviderProfileRequest.builder()
                    .preferredLanguage("english")
                    .build();

            Set<ConstraintViolation<ProviderProfileRequest>> violations = validator.validate(request);

            assertThat(violations).isNotEmpty();
            assertThat(violations)
                    .anyMatch(v -> v.getMessage().contains("Invalid language format"));
        }
    }
}
