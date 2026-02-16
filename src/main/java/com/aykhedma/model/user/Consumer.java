package com.aykhedma.model.user;

import com.aykhedma.model.booking.Booking;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "consumers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class Consumer extends User {

    @DecimalMin(value = "0.0", message = "Average rating cannot be negative")
    @DecimalMax(value = "5.0", message = "Average rating cannot exceed 5.0")
    //@Column(precision = 3, scale = 2)
    private Double averageRating;

    @OneToMany(mappedBy = "consumer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Booking> bookings = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "consumer_saved_providers",
            joinColumns = @JoinColumn(name = "consumer_id"),
            inverseJoinColumns = @JoinColumn(name = "provider_id")
    )
    @Size(max = 100, message = "Cannot save more than 100 providers")
    @Builder.Default
    private List<Provider> savedProviders = new ArrayList<>();

    @Min(value = 0, message = "Total bookings cannot be negative")
    @Column(nullable = false)
    private Integer totalBookings = 0;

    public void rateProvider(Long providerId, Integer rating, String review) {
    }

    public void reportProvider(Long providerId, String reason) {
    }

    public void updateProfile() {
    }
}