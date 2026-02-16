package com.aykhedma.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    @Pattern(regexp = "^[\\p{L} .'-]+$", message = "Name can only contain letters, spaces, dots, apostrophes, and hyphens")
    private String name;

    @Email(message = "Invalid email format")
    @Size(max = 100, message = "Email cannot exceed 100 characters")
    private String email;

    @Pattern(regexp = "^01[0-9]{9}$", message = "Phone number must be a valid Egyptian number")
    private String phoneNumber;

    @Size(min = 2, max = 10, message = "Language code must be between 2 and 10 characters")
    @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$", message = "Invalid language format (e.g., 'en', 'ar-EG')")
    private String preferredLanguage;

    @Size(max = 500, message = "Bio cannot exceed 500 characters")
    private String bio;

    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    @DecimalMax(value = "100000.0", message = "Price cannot exceed 100,000")
    private Double price;

    @Pattern(regexp = "^(HOUR|SESSION|VISIT)$", message = "Price type must be HOUR, SESSION, or VISIT")
    private String priceType;

    private Boolean emergencyEnabled;

    @DecimalMin(value = "0.1", message = "Service area must be at least 0.1 km")
    @DecimalMax(value = "100.0", message = "Service area cannot exceed 100 km")
    private Double serviceArea;
}