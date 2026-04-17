package com.aykhedma.repository;

import com.aykhedma.model.rating.InteractionRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InteractionRatingRepository extends JpaRepository<InteractionRating, Long> {
    List<InteractionRating> findByProviderId(Long providerId);
    List<InteractionRating> findByConsumerId(Long consumerId);
}
