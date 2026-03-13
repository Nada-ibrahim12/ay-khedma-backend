package com.aykhedma.dto.location;

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

@DisplayName("LocationDTO Validation Tests")
class LocationDtoTest {

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
        @DisplayName("Should pass validation with valid data")
        void validLocation_NoViolations() {
            LocationDTO dto = LocationDTO.builder()
                    .latitude(30.0444)
                    .longitude(31.2357)
                    .address("123 Street")
                    .area("Maadi")
                    .city("Cairo")
                    .build();

            Set<ConstraintViolation<LocationDTO>> violations = validator.validate(dto);

            assertThat(violations).isEmpty();
        }
    }

    @Nested
    @DisplayName("Latitude/Longitude Validation Tests")
    class LatLongValidationTests {

        @Test
        @DisplayName("Should fail when latitude is missing")
        void missingLatitude_HasViolation() {
            LocationDTO dto = LocationDTO.builder()
                    .longitude(31.2357)
                    .build();

            Set<ConstraintViolation<LocationDTO>> violations = validator.validate(dto);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getMessage().contains("Latitude is required"));
        }

        @Test
        @DisplayName("Should fail when longitude is missing")
        void missingLongitude_HasViolation() {
            LocationDTO dto = LocationDTO.builder()
                    .latitude(30.0444)
                    .build();

            Set<ConstraintViolation<LocationDTO>> violations = validator.validate(dto);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getMessage().contains("Longitude is required"));
        }

        @Test
        @DisplayName("Should fail when latitude is out of range")
        void latitudeOutOfRange_HasViolation() {
            LocationDTO dto = LocationDTO.builder()
                    .latitude(100.0)
                    .longitude(31.2357)
                    .build();

            Set<ConstraintViolation<LocationDTO>> violations = validator.validate(dto);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getMessage().contains("between -90 and 90"));
        }

        @Test
        @DisplayName("Should fail when longitude is out of range")
        void longitudeOutOfRange_HasViolation() {
            LocationDTO dto = LocationDTO.builder()
                    .latitude(30.0444)
                    .longitude(190.0)
                    .build();

            Set<ConstraintViolation<LocationDTO>> violations = validator.validate(dto);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getMessage().contains("between -180 and 180"));
        }
    }

    @Nested
    @DisplayName("Field Length Validation Tests")
    class FieldLengthValidationTests {

        @Test
        @DisplayName("Should fail when address is too long")
        void addressTooLong_HasViolation() {
            String longAddress = "A".repeat(300);
            LocationDTO dto = LocationDTO.builder()
                    .latitude(30.0444)
                    .longitude(31.2357)
                    .address(longAddress)
                    .build();

            Set<ConstraintViolation<LocationDTO>> violations = validator.validate(dto);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getMessage().contains("Address cannot exceed"));
        }

        @Test
        @DisplayName("Should fail when area is too long")
        void areaTooLong_HasViolation() {
            String longArea = "A".repeat(120);
            LocationDTO dto = LocationDTO.builder()
                    .latitude(30.0444)
                    .longitude(31.2357)
                    .area(longArea)
                    .build();

            Set<ConstraintViolation<LocationDTO>> violations = validator.validate(dto);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getMessage().contains("Area cannot exceed"));
        }

        @Test
        @DisplayName("Should fail when city is too long")
        void cityTooLong_HasViolation() {
            String longCity = "A".repeat(120);
            LocationDTO dto = LocationDTO.builder()
                    .latitude(30.0444)
                    .longitude(31.2357)
                    .city(longCity)
                    .build();

            Set<ConstraintViolation<LocationDTO>> violations = validator.validate(dto);

            assertThat(violations).isNotEmpty();
            assertThat(violations).anyMatch(v -> v.getMessage().contains("City cannot exceed"));
        }
    }
}
