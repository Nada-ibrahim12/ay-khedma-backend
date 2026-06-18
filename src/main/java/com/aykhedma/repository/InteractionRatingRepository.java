package com.aykhedma.repository;

import com.aykhedma.model.rating.InteractionRating;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InteractionRatingRepository extends JpaRepository<InteractionRating, Long> {
    List<InteractionRating> findByProviderId(Long providerId);
    List<InteractionRating> findByConsumerId(Long consumerId);

    @Modifying
    @Query("DELETE FROM InteractionRating ir WHERE ir.consumer.id = :consumerId")
    void deleteByConsumerId(@Param("consumerId") Long consumerId);

    @Modifying
    @Query("DELETE FROM InteractionRating ir WHERE ir.provider.id = :providerId")
    void deleteByProviderId(@Param("providerId") Long providerId);
}
