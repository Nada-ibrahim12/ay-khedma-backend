package com.aykhedma.model.user;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.JOINED)
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    @Column(nullable = false, length = 100)
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 100, message = "Email cannot exceed 100 characters")
    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^01[0-9]{9}$", message = "Phone number must be a valid Egyptian number (01xxxxxxxxx)")
    @Column(unique = true, nullable = false, length = 11)
    private String phoneNumber;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 60, message = "Invalid password hash")
    @Column(nullable = false, length = 60)
    private String password;

    @PastOrPresent(message = "Created date cannot be in the future")
    @CreationTimestamp
    private LocalDateTime createdAt;

    @NotNull(message = "User role is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserType role;

    @Size(max = 255, message = "Profile image URL cannot exceed 255 characters")
    @Pattern(regexp = "^(http|https|ftp)://.*$", message = "Invalid URL format")
    private String profileImage;

    @Size(min = 2, max = 10, message = "Language code must be between 2 and 10 characters")
    @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$", message = "Invalid language format (e.g., 'en', 'ar-EG')")
    private String preferredLanguage;

    @NotNull(message = "Account enabled status is required")
    @Column(nullable = false)
    private boolean enabled = true;

    @NotNull(message = "Credentials non-expired status is required")
    @Column(nullable = false)
    private boolean credentialsNonExpired = true;

    public UserType getRole() {
        return role;
    }

    public void setRole(UserType role) {
        this.role = role;
    }
}