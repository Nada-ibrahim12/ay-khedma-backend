package com.aykhedma.model.booking;

import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.Provider;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "bookings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Consumer is required")
    @ManyToOne
    @JoinColumn(name = "consumer_id", nullable = false)
    private Consumer consumer;

    @NotNull(message = "Provider is required")
    @ManyToOne
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @NotNull(message = "Service type is required")
    @ManyToOne
    @JoinColumn(name = "service_type_id", nullable = false)
    private ServiceType serviceType;

    @NotNull(message = "Requested date is required")
    @FutureOrPresent(message = "Requested date must be today or in the future")
    @Column(nullable = false)
    private LocalDate requestedDate;

    @NotNull(message = "Requested start time is required")
    @Column(nullable = false)
    private LocalTime requestedStartTime;

    private LocalTime requestedEndTime;

    @Size(max = 1000, message = "Problem description cannot exceed 1000 characters")
    @Column(length = 1000)
    private String problemDescription;

    @DecimalMin(value = "0.0", inclusive = false, message = "Initial price must be greater than 0")
    @DecimalMax(value = "100000.0", message = "Initial price cannot exceed 100,000")
    private Double initialPrice;

    @DecimalMin(value = "0.0", inclusive = false, message = "Final price must be greater than 0")
    @DecimalMax(value = "100000.0", message = "Final price cannot exceed 100,000")
    private Double finalPrice;

    @NotNull(message = "Booking status is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.PENDING;

    private LocalTime estimatedTime;

    @PastOrPresent(message = "Accepted date cannot be in the future")
    private LocalDateTime acceptedAt;

    @PastOrPresent(message = "Started date cannot be in the future")
    private LocalDateTime startedAt;

    @PastOrPresent(message = "Completed date cannot be in the future")
    private LocalDateTime completedAt;

    @PastOrPresent(message = "Cancelled date cannot be in the future")
    private LocalDateTime cancelledAt;

    @Size(max = 200, message = "Cancellation reason cannot exceed 200 characters")
    private String cancellationReason;

    @PastOrPresent(message = "Created date cannot be in the future")
    @CreationTimestamp
    private LocalDateTime createdAt;

    @PastOrPresent(message = "Updated date cannot be in the future")
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating cannot exceed 5")
    private Double consumerRating;

    @Size(max = 500, message = "Consumer review cannot exceed 500 characters")
    @Column(length = 500)
    private String consumerReview;

    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating cannot exceed 5")
    private Double providerRating;

    @Size(max = 500, message = "Provider review cannot exceed 500 characters")
    @Column(length = 500)
    private String providerReview;

    // Link to the booked time slot
    @OneToOne
    @JoinColumn(name = "time_slot_id")
    private TimeSlot timeSlot;

    // These validation methods need to be updated to work with the new Schedule model
    @AssertTrue(message = "Requested time must be within provider's working hours")
    private boolean isValidRequestedTime() {
        if (requestedStartTime == null || provider == null || provider.getSchedule() == null) return true;

        // Find the working day for this date
        WorkingDay workingDay = provider.getSchedule().getWorkingDays().stream()
                .filter(wd -> wd.getDayOfWeek() == requestedDate.getDayOfWeek())
                .findFirst()
                .orElse(null);

        if (workingDay == null) return false;

        // Calculate end time if not set
        LocalTime endTime = requestedEndTime != null ? requestedEndTime :
                requestedStartTime.plusHours(1); // Default 1 hour if not set

        return !requestedStartTime.isBefore(workingDay.getStartTime()) &&
                !endTime.isAfter(workingDay.getEndTime());
    }

    @AssertTrue(message = "Requested date must be a working day")
    private boolean isValidRequestedDate() {
        if (requestedDate == null || provider == null || provider.getSchedule() == null) return true;

        return provider.getSchedule().getWorkingDays().stream()
                .anyMatch(wd -> wd.getDayOfWeek() == requestedDate.getDayOfWeek());
    }
}