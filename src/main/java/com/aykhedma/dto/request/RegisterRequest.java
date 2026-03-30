package com.aykhedma.dto.request;

import com.aykhedma.model.user.UserType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    @Pattern(regexp = "^[\\p{L} .'-]+$", message = "Name can only contain letters, spaces, dots, apostrophes, and hyphens")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 100, message = "Email cannot exceed 100 characters")
    private String email;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^01[0-9]{9}$", message = "Phone number must be a valid Egyptian number (01xxxxxxxxx)")
    private String phoneNumber;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 32, message = "Password must be between 8 and 32 characters")
    // @Pattern(regexp =
    // "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!])(?=\\S+$).{8,}$",
    // message = "Password must contain at least one digit, one lowercase, one
    // uppercase, and one special character")
    private String password;

    @NotNull(message = "User type is required")
    private UserType userType;

    // Provider specific fields
    @Size(max = 500, message = "Bio cannot exceed 500 characters")
    private String bio;

    @Min(value = 0, message = "Years of experience cannot be negative")
    private Integer yearsOfExperience;

    @Positive(message = "Service type ID must be positive")
    private Long serviceTypeId;

    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    @DecimalMax(value = "100000.0", message = "Price cannot exceed 100,000")
    private Double price;

    @Pattern(regexp = "^(HOUR|SESSION|VISIT)$", message = "Price type must be HOUR, SESSION, or VISIT")
    private String priceType;

    @Pattern(regexp = "^[0-9]{14}$", message = "National ID must be exactly 14 digits")
    private String nationalId;

    @Size(max = 100, message = "Service area cannot exceed 100 characters")
    private String serviceArea;

    @DecimalMin(value = "0.0", message = "Service area radius cannot be negative")
    private Double serviceAreaRadius;

    // Consumer specific fields
    @Size(min = 2, max = 10, message = "Language code must be between 2 and 10 characters")
    @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$", message = "Invalid language format (e.g., 'en', 'ar-EG')")
    private String preferredLanguage;

    // Location fields
    @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    private Double longitude;

    @Size(max = 255, message = "Address cannot exceed 255 characters")
    private String address;

    @Size(max = 100, message = "Area cannot exceed 100 characters")
    private String area;

    @Size(max = 100, message = "City cannot exceed 100 characters")
    private String city;

    @AssertTrue(message = "Service type is required for providers")
    public boolean isProviderServiceTypeValid() {
        return userType != UserType.PROVIDER || serviceTypeId != null;
    }

    @AssertTrue(message = "Price is required for providers")
    public boolean isProviderPriceValid() {
        return userType != UserType.PROVIDER || price != null;
    }

    @AssertTrue(message = "Price type is required for providers")
    public boolean isProviderPriceTypeValid() {
        return userType != UserType.PROVIDER || hasText(priceType);
    }

    @AssertTrue(message = "National ID is required for providers")
    public boolean isProviderNationalIdValid() {
        return userType != UserType.PROVIDER || hasText(nationalId);
    }

    @AssertTrue(message = "Service area is required for providers")
    public boolean isProviderServiceAreaValid() {
        return userType != UserType.PROVIDER || hasText(serviceArea);
    }

    @AssertTrue(message = "Service area radius is required for providers")
    public boolean isProviderServiceAreaRadiusValid() {
        return userType != UserType.PROVIDER || serviceAreaRadius != null;
    }

    @AssertTrue(message = "Provider location latitude is required")
    public boolean isProviderLatitudeValid() {
        return userType != UserType.PROVIDER || latitude != null;
    }

    @AssertTrue(message = "Provider location longitude is required")
    public boolean isProviderLongitudeValid() {
        return userType != UserType.PROVIDER || longitude != null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

}