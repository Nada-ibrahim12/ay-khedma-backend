package com.aykhedma.model.user;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "admins")
@Data
@NoArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@PrimaryKeyJoinColumn(name = "id")
@DiscriminatorValue("ADMIN")
public class Admin extends User {


    public VerificationStatus verifyProvider(Provider provider, VerificationStatus status, String notes) {
        provider.setVerificationStatus(status);
        return status;
    }

    public void manageUsers(User user, String action) {
    }
}