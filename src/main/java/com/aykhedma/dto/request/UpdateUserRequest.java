package com.aykhedma.dto.request;

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
public class UpdateUserRequest {
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @Pattern(regexp = "^01[0-9]{9}$", message = "Phone number must be a valid Egyptian number")
    private String phoneNumber;

    @Size(max = 100, message = "Email cannot exceed 100 characters")
    private String email;

    @Size(min = 8, max = 32, message = "Password must be between 8 and 32 characters")
    private String password;

    private Boolean enabled;
}
