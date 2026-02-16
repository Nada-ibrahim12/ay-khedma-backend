package com.aykhedma.model.user;

import com.aykhedma.model.booking.Booking;
import com.aykhedma.model.booking.Schedule;
import com.aykhedma.model.document.Document;
import com.aykhedma.model.location.Location;
import com.aykhedma.model.service.PriceType;
import com.aykhedma.model.service.ServiceType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "providers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class Provider extends User {

    @Size(max = 500, message = "Bio cannot exceed 500 characters")
    @Column(length = 500)
    private String bio;

    @NotNull(message = "Verification status is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Min(value = 1, message = "Average time must be at least 1 minute")
    @Max(value = 480, message = "Average time cannot exceed 8 hours (480 minutes)")
    private Integer averageTime;

    @Min(value = 0, message = "Completed jobs cannot be negative")
    @Column(nullable = false)
    private Integer completedJobs = 0;

    @NotNull(message = "Service type is required")
    @ManyToOne
    @JoinColumn(name = "service_type_id", nullable = false)
    private ServiceType serviceType;

    @OneToMany(mappedBy = "provider", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Size(max = 20, message = "Cannot have more than 20 documents")
    @Builder.Default
    private List<Document> documents = new ArrayList<>();

    @NotNull(message = "Location is required")
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @NotNull(message = "Emergency enabled status is required")
    @Column(nullable = false)
    private Boolean emergencyEnabled = false;

    @NotBlank(message = "National ID is required")
    @Pattern(regexp = "^[0-9]{14}$", message = "National ID must be exactly 14 digits")
    @Column(unique = true, nullable = false, length = 14)
    private String nationalId;

    @NotNull(message = "Schedule is required")
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @DecimalMin(value = "0.0", message = "Average rating cannot be negative")
    @DecimalMax(value = "5.0", message = "Average rating cannot exceed 5.0")
    @Column(precision = 3, scale = 2)
    private Double averageRating = 0.0;

    @Min(value = 0, message = "Booking rate cannot be negative")
    @Max(value = 100, message = "Booking rate cannot exceed 100")
    private Integer bookingRate = 0;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    @DecimalMax(value = "100000.0", message = "Price cannot exceed 100,000")
    @Column(nullable = false, precision = 10, scale = 2)
    private Double price;

    @NotNull(message = "Price type is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PriceType priceType;

    @DecimalMin(value = "0.1", message = "Service area must be at least 0.1 km")
    @DecimalMax(value = "100.0", message = "Service area cannot exceed 100 km")
    private Double serviceArea;

    @OneToMany(mappedBy = "provider")
    @Builder.Default
    private List<Booking> bookings = new ArrayList<>();

    @Min(value = 0, message = "Acceptance rate cannot be negative")
    @Max(value = 100, message = "Acceptance rate cannot exceed 100")
    private Integer acceptanceRate = 100;

    @Min(value = 0, message = "Response time cannot be negative")
    @Max(value = 60, message = "Response time cannot exceed 60 minutes")
    private Integer responseTime;
}