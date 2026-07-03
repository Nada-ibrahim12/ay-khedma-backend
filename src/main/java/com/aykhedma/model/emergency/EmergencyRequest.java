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

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
    @DecimalMax(value = "100000.0", message = "Price cannot exceed 100,000")
    private Double price;

    @Builder.Default
    @NotNull(message = "Emergency status is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmergencyRequestStatus status = EmergencyRequestStatus.BROADCASTING;

    @OneToMany(mappedBy = "emergencyRequest", cascade = CascadeType.ALL)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<ProviderResponse> providerResponses = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "selected_provider_id")
    private Provider selectedProvider;

    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating cannot exceed 5")
    private Double consumerRating;

    @Size(max = 500, message = "Consumer review cannot exceed 500 characters")
    @Column(length = 500)
    private String consumerReview;

    @Min(value = 1, message = "Punctuality rating must be at least 1")
    @Max(value = 5, message = "Punctuality rating cannot exceed 5")
    private Double punctualityRating;

    @Min(value = 1, message = "Commitment rating must be at least 1")
    @Max(value = 5, message = "Commitment rating cannot exceed 5")
    private Double commitmentRating;

    @Min(value = 1, message = "Quality of work rating must be at least 1")
    @Max(value = 5, message = "Quality of work rating cannot exceed 5")
    private Double qualityOfWorkRating;

    @Min(value = 1, message = "Provider rating must be at least 1")
    @Max(value = 5, message = "Provider rating cannot exceed 5")
    private Double providerRating;

    @Size(max = 500, message = "Provider review cannot exceed 500 characters")
    @Column(length = 500)
    private String providerReview;

    private LocalDateTime completedAt;

    @PastOrPresent(message = "Created date cannot be in the future")
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Size(max = 1000, message = "Description cannot exceed 500 characters")
    @Column(length = 1000)
    private String description;

    @Builder.Default
    @Min(value = 5, message = "Search radius must be at least 5 km")
    @Max(value = 50, message = "Search radius cannot exceed 50 km")
    private Integer searchRadius = 5;

    public List<ProviderResponse> getAcceptedProviders() {
        return providerResponses.stream()
                .filter(ProviderResponse::isAccepted)
                .toList();
    }
}