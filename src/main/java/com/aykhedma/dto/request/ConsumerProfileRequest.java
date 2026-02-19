package com.aykhedma.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerProfileRequest {

    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @Email(message = "Invalid email format")
    private String email;

    @Pattern(regexp = "^01[0-9]{9}$", message = "Phone number must be a valid Egyptian number")
    private String phoneNumber;

    @Size(min = 2, max = 10, message = "Language code must be between 2 and 10 characters")
    private String preferredLanguage;
}