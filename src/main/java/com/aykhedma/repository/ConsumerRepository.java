package com.aykhedma.repository;

import com.aykhedma.model.user.Consumer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConsumerRepository extends JpaRepository<Consumer, Long> {

    Optional<Consumer> findByEmail(String email);

    Optional<Consumer> findByPhoneNumber(String phoneNumber);

    @Query("SELECT c FROM Consumer c WHERE c.averageRating >= :minRating")
    List<Consumer> findTopRatedConsumers(@Param("minRating") Double minRating);

    @Modifying
    @Query("UPDATE Consumer c SET c.profileImage = :profileImage WHERE c.id = :consumerId")
    void updateProfileImage(@Param("consumerId") Long consumerId, @Param("profileImage") String profileImage);

    @Query(value = "SELECT COUNT(*) > 0 FROM consumer_saved_providers " +
            "WHERE consumer_id = :consumerId AND provider_id = :providerId",
            nativeQuery = true)
    boolean isProviderSavedNative(@Param("consumerId") Long consumerId,
                                  @Param("providerId") Long providerId);

    @Modifying
    @Query(value = "INSERT INTO consumer_saved_providers (consumer_id, provider_id) " +
            "VALUES (:consumerId, :providerId)", nativeQuery = true)
    void insertSavedProvider(@Param("consumerId") Long consumerId,
                             @Param("providerId") Long providerId);

    @Modifying
    @Query(value = "DELETE FROM consumer_saved_providers " +
            "WHERE consumer_id = :consumerId AND provider_id = :providerId",
            nativeQuery = true)
    int deleteSavedProvider(@Param("consumerId") Long consumerId,
                            @Param("providerId") Long providerId);

    @Modifying
    @Query("UPDATE Consumer c SET c.location.id = :locationId WHERE c.id = :consumerId")
    void updateConsumerLocation(@Param("consumerId") Long consumerId, @Param("locationId") Long locationId);

    // Check if consumer exists without loading the entity
    boolean existsById(Long id);
}