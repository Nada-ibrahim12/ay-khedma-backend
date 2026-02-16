package com.aykhedma.model.user;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "admins")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class Admin extends User {

    @NotBlank(message = "Department is required")
    @Size(min = 2, max = 50, message = "Department must be between 2 and 50 characters")
    @Column(nullable = false, length = 50)
    private String department;

    @Size(max = 500, message = "Permissions string cannot exceed 500 characters")
    private String permissions; // JSON string or separate entity

    public VerificationStatus verifyProvider(Provider provider, VerificationStatus status, String notes) {
        provider.setVerificationStatus(status);
        return status;
    }

    public void manageUsers(User user, String action) {
    }
}