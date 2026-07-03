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
@Table(name = "providers", indexes = {
        @Index(name = "idx_provider_verification_status", columnList = "verification_status"),
        @Index(name = "idx_provider_service_type", columnList = "service_type_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@PrimaryKeyJoinColumn(name = "id")
@DiscriminatorValue("PROVIDER")
public class Provider extends User {

    @Size(max = 500, message = "Bio cannot exceed 500 characters")
    @Column(length = 500)
    private String bio;

    @Min(value = 0, message = "Years of experience cannot be negative")
    private Integer yearsOfExperience;

    @Builder.Default
    @NotNull(message = "Verification status is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Min(value = 1, message = "Average time must be at least 1 minute")
    @Max(value = 480, message = "Average time cannot exceed 8 hours (480 minutes)")
    private Integer averageTime;

    @Builder.Default
    @Min(value = 0, message = "Completed jobs cannot be negative")
    @Column(nullable = false)
    private Integer completedJobs = 0;

    @Builder.Default
    @Min(value = 0, message = "Total bookings cannot be negative")
    @Column(nullable = false)
    private Integer totalBookings = 0;

    @Builder.Default
    @Min(value = 0, message = "Total bookings cannot be negative")
    @Column(nullable = false)
    private Integer cancelledBookings = 0;

    @Builder.Default
    @Min(value = 0, message = "Total requests cannot be negative")
    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer totalRequests = 0;

    @NotNull(message = "Service type is required")
    @ManyToOne
    @JoinColumn(name = "service_type_id", nullable = false)
    private ServiceType serviceType;

    @Size(max = 255, message = "Works at cannot exceed 200 characters")
    @Column(length = 255)
    private String worksAt;

    @Size(max = 255, message = "Work location cannot exceed 255 characters")
    @Column(length = 255)
    private String workLocation;

    @OneToMany(mappedBy = "provider", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Size(max = 20, message = "Cannot have more than 20 documents")
    @Builder.Default
    private List<Document> documents = new ArrayList<>();

    @NotNull(message = "Location is required")
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Builder.Default
    @NotNull(message = "Emergency enabled status is required")
    @Column(nullable = false)
    private Boolean emergencyEnabled = false;

    @NotBlank(message = "National ID is required")
    @Pattern(regexp = "^[0-9]{14}$", message = "National ID must be exactly 14 digits")
    @Column(unique = true, nullable = false, length = 14)
    private String nationalId;

    @Size(max = 500, message = "National ID front image URL cannot exceed 500 characters")
    @Column(length = 500)
    private String nationalIdFrontImage;

    @Size(max = 500, message = "National ID back image URL cannot exceed 500 characters")
    @Column(length = 500)
    private String nationalIdBackImage;

    @Size(max = 500, message = "Selfie image URL cannot exceed 500 characters")
    @Column(length = 500)
    private String selfieImage;

    @NotNull(message = "Schedule is required")
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "schedule_id", nullable = false)
    private Schedule schedule;

    @Builder.Default
    @DecimalMin(value = "0.0", message = "Average punctuality cannot be negative")
    @DecimalMax(value = "5.0", message = "Average punctuality cannot exceed 5.0")
    private Double averagePunctualityRating = 0.0;

    @Builder.Default
    @DecimalMin(value = "0.0", message = "Average commitment cannot be negative")
    @DecimalMax(value = "5.0", message = "Average commitment cannot exceed 5.0")
    private Double averageCommitmentRating = 0.0;

    @Builder.Default
    @DecimalMin(value = "0.0", message = "Average quality cannot be negative")
    @DecimalMax(value = "5.0", message = "Average quality cannot exceed 5.0")
    private Double averageQualityOfWorkRating = 0.0;

    @Builder.Default
    @DecimalMin(value = "0.0", message = "Average rating cannot be negative")
    @DecimalMax(value = "5.0", message = "Average rating cannot exceed 5.0")
    // @Column(precision = 3, scale = 2)
    private Double averageRating = 0.0;

    @Builder.Default
    @DecimalMin(value = "0.0", message = "Average interaction rating cannot be negative")
    @DecimalMax(value = "5.0", message = "Average interaction rating cannot exceed 5.0")
    @Column(nullable = false, columnDefinition = "double precision default 0.0")
    private Double averageInteractionRating = 0.0;

    @Builder.Default
    @Min(value = 0, message = "Interaction rating count cannot be negative")
    @Column(nullable = false, columnDefinition = "integer default 0")
    private Integer interactionRatingCount = 0;

    @Builder.Default
    @Min(value = 0, message = "Booking rate cannot be negative")
    @Max(value = 100, message = "Booking rate cannot exceed 100")
    private Integer bookingRate = 0;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    @DecimalMax(value = "100000.0", message = "Price cannot exceed 100,000")
    // @Column(nullable = false, precision = 10, scale = 2)
    private Double price;

    @NotNull(message = "Price type is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PriceType priceType;

    @Builder.Default
    @DecimalMin(value = "0.0", message = "Service area radius cannot be negative")
    private Double serviceAreaRadius = 7.0;

    @OneToMany(mappedBy = "provider")
    @Builder.Default
    private List<Booking> bookings = new ArrayList<>();

    @Builder.Default
    @Min(value = 0, message = "Acceptance rate cannot be negative")
    @Max(value = 100, message = "Acceptance rate cannot exceed 100")
    private Integer acceptanceRate = 100;

    @Builder.Default
    @DecimalMin(value = "0.0", message = "Cancellation rate cannot be negative")
    @DecimalMax(value = "100.0", message = "Cancellation rate cannot exceed 100.0")
    private Double cancellationRate = 0.0;

    @Min(value = 0, message = "Response time cannot be negative")
    @Max(value = 60, message = "Response time cannot exceed 60 minutes")
    private Integer responseTime;

    @Min(value = 0, message = "Booking buffer cannot be negative")
    @Max(value = 480, message = "Booking buffer cannot exceed 480 minutes")
    @Builder.Default
    @Column(nullable = false)
    private Integer bookingBufferMinutes = 30;

    @Size(max = 500, message = "Rejection reason cannot exceed 500 characters")
    @Column(length = 500)
    private String rejectionReason;

    @Builder.Default
    @Column(name = "is_nid_verified", columnDefinition = "boolean default false")
    private Boolean nidVerified = false;

    @Builder.Default
    @Column(name = "is_face_matched", columnDefinition = "boolean default false")
    private Boolean faceMatched = false;

    private Double faceMatchConfidence;

    public Double getCancellationRate() {
        if (totalBookings == null || totalBookings == 0) {
            return 0.0;
        }
        return Math.round(((double) cancelledBookings / totalBookings) * 100.0 * 10.0) / 10.0;
    }

    public Double getAverageJobs() {
        if (completedJobs == null || completedJobs == 0 || super.getCreatedAt() == null) {
            return 0.0;
        }
        java.time.LocalDateTime created = super.getCreatedAt();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        long months = java.time.temporal.ChronoUnit.MONTHS.between(
                java.time.LocalDate.of(created.getYear(), created.getMonth(), 1),
                java.time.LocalDate.of(now.getYear(), now.getMonth(), 1));
        if (months <= 0)
            months = 1;
        double avg = (double) completedJobs / (double) months;
        return Math.round(avg * 10.0) / 10.0;
    }
}