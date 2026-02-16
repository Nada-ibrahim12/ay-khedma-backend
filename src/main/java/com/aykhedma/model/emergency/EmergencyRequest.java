package com.aykhedma.model.emergency;

import com.aykhedma.model.location.Location;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.Provider;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "emergency_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmergencyRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Consumer is required")
    @ManyToOne
    @JoinColumn(name = "consumer_id", nullable = false)
    private Consumer consumer;

    @NotNull(message = "Service type is required")
    @ManyToOne
    @JoinColumn(name = "service_type_id", nullable = false)
    private ServiceType serviceType;

    @NotNull(message = "Location is required")
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @NotNull(message = "Emergency status is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmergencyStatus status = EmergencyStatus.BROADCASTING;

    @DecimalMin(value = "1.0", message = "Emergency fee multiplier must be at least 1.0")
    @DecimalMax(value = "3.0", message = "Emergency fee multiplier cannot exceed 3.0")
    @Column(nullable = false)
    private Double emergencyFeeMultiplier = 1.5;

    @OneToMany(mappedBy = "emergencyRequest", cascade = CascadeType.ALL)
    @Builder.Default
    private List<ProviderResponse> providerResponses = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "selected_provider_id")
    private Provider selectedProvider;

    @PastOrPresent(message = "Created date cannot be in the future")
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Future(message = "Expiry date must be in the future")
    private LocalDateTime expiresAt;

    @Size(max = 500, message = "Description cannot exceed 500 characters")
    @Column(length = 500)
    private String description;

    @Min(value = 1, message = "Search radius must be at least 1 km")
    @Max(value = 50, message = "Search radius cannot exceed 50 km")
    private Integer searchRadius = 10;

    public List<ProviderResponse> getAcceptedProviders() {
        return providerResponses.stream()
                .filter(ProviderResponse::isAccepted)
                .toList();
    }
}