package com.aykhedma.model.service;

import com.aykhedma.model.user.Provider;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "service_types")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Service type name is required")
    @Size(min = 2, max = 50, message = "Service type name must be between 2 and 50 characters")
    @Column(unique = true, nullable = false, length = 50)
    private String name;

    @Size(max = 50, message = "Arabic name cannot exceed 50 characters")
    @Column(name = "name_ar", length = 50)
    private String nameAr;

    @Size(max = 200, message = "Description cannot exceed 200 characters")
    @Column(length = 200)
    private String description;

    @NotNull(message = "Category is required")
    @ManyToOne
    @JoinColumn(name = "category_id", nullable = false)
    private ServiceCategory category;

    @NotNull(message = "Risk level is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RiskLevel riskLevel = RiskLevel.LOW;

    @DecimalMin(value = "0.0", inclusive = false, message = "Base price must be greater than 0")
    @DecimalMax(value = "100000.0", message = "Base price cannot exceed 100,000")
    //@Column(precision = 10, scale = 2)
    private Double basePrice;

    @NotNull(message = "Default price type is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PriceType defaultPriceType;

    @Min(value = 15, message = "Estimated duration must be at least 15 minutes")
    @Max(value = 480, message = "Estimated duration cannot exceed 8 hours (480 minutes)")
    private Integer estimatedDuration;

    @OneToMany(mappedBy = "serviceType")
    @Builder.Default
    private List<Provider> providers = new ArrayList<>();

    public RiskLevel isHighRisk() {
        return riskLevel;
    }

    public String getName(String language) {
        if ("ar".equals(language) && nameAr != null && !nameAr.isEmpty()) {
            return nameAr;
        }
        return name;
    }
}