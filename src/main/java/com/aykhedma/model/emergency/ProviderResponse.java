package com.aykhedma.model.emergency;

import com.aykhedma.model.location.Location;
import com.aykhedma.model.user.Provider;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "provider_responses")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Provider is required")
    @ManyToOne
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @NotNull(message = "Emergency request is required")
    @ManyToOne
    @JoinColumn(name = "emergency_request_id", nullable = false)
    private EmergencyRequest emergencyRequest;

    @NotNull(message = "Response type is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProviderResponseType responseType = ProviderResponseType.NO_RESPONSE;

    @PastOrPresent(message = "Response time cannot be in the future")
    @CreationTimestamp
    private LocalDateTime responseTime;

    @Size(max = 200, message = "Notes cannot exceed 200 characters")
    @Column(length = 200)
    private String notes;

    @NotNull(message = "Proposed price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Proposed price must be greater than 0")
    @DecimalMax(value = "100000.0", message = "Proposed price cannot exceed 100,000")
    @Column(nullable = false, precision = 10, scale = 2)
    private Double proposedPrice;

    @Min(value = 1, message = "Estimated arrival time must be at least 1 minute")
    @Max(value = 120, message = "Estimated arrival time cannot exceed 2 hours (120 minutes)")
    private Integer estimatedArrivalTime;

    @DecimalMin(value = "0.0", message = "Distance cannot be negative")
    @DecimalMax(value = "100.0", message = "Distance cannot exceed 100 km")
    @Column(precision = 10, scale = 2)
    private Double distance;

    private Boolean selected = false;

    @AssertTrue(message = "Proposed price must be at least the base price times emergency multiplier")
    private boolean isValidProposedPrice() {
        if (proposedPrice == null || emergencyRequest == null ||
                emergencyRequest.getServiceType() == null ||
                emergencyRequest.getServiceType().getBasePrice() == null) return true;

        double minPrice = emergencyRequest.getServiceType().getBasePrice() *
                emergencyRequest.getEmergencyFeeMultiplier();
        return proposedPrice >= minPrice;
    }

    public boolean isAccepted() {
        return responseType == ProviderResponseType.ACCEPTED_OFFER;
    }

    public Integer estimateArrivalTime(Location providerLocation) {
        if (emergencyRequest.getLocation() != null && providerLocation != null) {
            double distance = providerLocation.calculateDistance(emergencyRequest.getLocation());
            // Assuming average speed of 30 km/h in city
            int estimatedMinutes = (int) Math.ceil(distance / 30 * 60);
            this.estimatedArrivalTime = Math.min(estimatedMinutes, 120);
            return this.estimatedArrivalTime;
        }
        return estimatedArrivalTime;
    }
}