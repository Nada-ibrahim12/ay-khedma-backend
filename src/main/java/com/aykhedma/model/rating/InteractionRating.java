package com.aykhedma.model.rating;

import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.Provider;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "interaction_ratings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InteractionRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Consumer is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consumer_id", nullable = false)
    private Consumer consumer;

    @NotNull(message = "Provider is required")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating cannot exceed 5")
    private Double rating;

    @Size(max = 500, message = "Comment cannot exceed 500 characters")
    @Column(length = 500)
    private String comment;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}
