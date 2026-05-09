package com.aykhedma.repository;

import com.aykhedma.dto.response.ProviderDistanceProjection;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.user.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.locationtech.jts.geom.Point;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, Long> {

        Optional<Provider> findByEmail(String email);

        Optional<Provider> findByPhoneNumber(String phoneNumber);

        boolean existsByNationalId(String nationalId);

        boolean existsByNationalIdAndIdNot(String nationalId, Long id);

        List<Provider> findByVerificationStatus(VerificationStatus status);

        List<Provider> findByServiceType(ServiceType serviceType);

        @Query("SELECT p FROM Provider p WHERE p.serviceType = :serviceType AND p.verificationStatus = 'VERIFIED' AND p.emergencyEnabled = true")
        List<Provider> findVerifiedEmergencyProviders(@Param("serviceType") ServiceType serviceType);

        @Query("SELECT p FROM Provider p WHERE p.serviceType.id = :serviceTypeId AND p.verificationStatus = 'VERIFIED'")
        List<Provider> findVerifiedByServiceType(@Param("serviceTypeId") Long serviceTypeId);

        @Query("SELECT p FROM Provider p WHERE p.verificationStatus = 'VERIFIED' AND p.emergencyEnabled = true")
        List<Provider> findAllEmergencyProviders();

        @Query(value = "SELECT p.* FROM providers p " +
                        "JOIN locations l ON p.location_id = l.id " +
                        "WHERE p.verification_status = 'VERIFIED' " +
                        "AND p.service_type_id = :serviceTypeId " +
                        "AND ST_DWithin(l.coordinates, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326), :radius)", nativeQuery = true)
        List<Provider> findNearbyProviders(@Param("latitude") Double latitude,
                        @Param("longitude") Double longitude,
                        @Param("radius") Double radius,
                        @Param("serviceTypeId") Long serviceTypeId);

        @Modifying
        @Query("UPDATE Provider p SET p.profileImage = :profileImage WHERE p.id = :providerId")
        void updateProfileImage(@Param("providerId") Long providerId, @Param("profileImage") String profileImage);

        List<Provider> findByServiceTypeId(Long serviceTypeId);

        boolean existsByServiceTypeId(Long serviceTypeId);

        @Query("SELECT p FROM Provider p " +
                        "LEFT JOIN p.serviceType s " +
                        "LEFT JOIN s.category c " +
                        "LEFT JOIN p.location l " +
                        "WHERE p.enabled = true " +
                        "AND p.verificationStatus = 'VERIFIED' " +
                        "AND (:keyword IS NULL OR :keyword = '' OR " +
                        "    LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "    LOWER(COALESCE(p.bio, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "    LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "    LOWER(COALESCE(s.nameAr, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "    LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "    LOWER(COALESCE(l.area, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                        "AND (:categoryId IS NULL OR c.id = :categoryId) " +
                        "AND (:categoryName IS NULL OR :categoryName = '' OR " +
                        "    LOWER(c.name) = LOWER(:categoryName))")
        Page<Provider> searchProviders(
                        @Param("keyword") String keyword,
                        @Param("categoryId") Long categoryId,
                        @Param("categoryName") String categoryName,
                        Pageable pageable);

        @Modifying
        @Query("UPDATE Provider p SET p.totalBookings = p.totalBookings + 1 WHERE p.id = :providerId")
        void incrementTotalBookings(@Param("providerId") Long providerId);

        @Modifying
        @Query("UPDATE Provider p SET p.totalRequests = p.totalRequests + 1 WHERE p.id = :providerId")
        void incrementTotalRequests(@Param("providerId") Long providerId);

        @Modifying
        @Query("UPDATE Provider p SET p.cancelledBookings = p.cancelledBookings + 1 WHERE p.id = :providerId")
        void incrementCancelledBookings(@Param("providerId") Long providerId);

        @Modifying
        @Query("UPDATE Provider p SET p.completedJobs = p.completedJobs + 1 WHERE p.id = :providerId")
        void incrementCompletedJobs(@Param("providerId") Long providerId);

        @Modifying
        @Query("UPDATE Provider p SET p.acceptanceRate = :acceptanceRate, p.bookingRate = :bookingRate WHERE p.id = :providerId")
        void updateRates(@Param("providerId") Long providerId,
                        @Param("acceptanceRate") Integer acceptanceRate,
                        @Param("bookingRate") Integer bookingRate);

        @Query("SELECT p FROM Provider p " +
                        "LEFT JOIN p.serviceType s " +
                        "LEFT JOIN s.category c " +
                        "WHERE (:status IS NULL OR p.verificationStatus = :status) " +
                        "AND (:enabled IS NULL OR p.enabled = :enabled) " +
                        "AND (:keyword IS NULL OR :keyword = '' OR " +
                        "    LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "    LOWER(p.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "    LOWER(p.phoneNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "    LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')))")
        Page<Provider> findAllProvidersForAdmin(
                        @Param("keyword") String keyword,
                        @Param("status") VerificationStatus status,
                        @Param("enabled") Boolean enabled,
                        Pageable pageable);

        // for AI
        List<Provider> findByServiceTypeIdAndVerificationStatus(Long serviceTypeId, VerificationStatus status);
  
      @Query(value = "SELECT p.*, u.* FROM providers p " +
            "JOIN users u ON p.id = u.id " +
            "JOIN locations l ON p.location_id = l.id " +
            "WHERE p.service_type_id = :serviceTypeId " +
            "AND p.emergency_enabled = true " +
            "AND p.verification_status = 'VERIFIED' " +
            "AND ST_DWithin(l.coordinates, :requestCoordinates, :radiusMeters)",
            nativeQuery = true)
    List<Provider> findProvidersWithinRadius(@Param("serviceTypeId") Long serviceTypeId,
                                             @Param("requestCoordinates") Point requestCoordinates,
                                             @Param("radiusMeters") double radiusMeters);

        @Query(value = """
WITH consumer_location AS (
    SELECT cl.coordinates AS consumer_coords
    FROM consumers c
    JOIN locations cl ON c.location_id = cl.id
    WHERE c.id = :consumerId
),
provider_distance AS (
    SELECT
        p.id AS id,
        u.name AS name,
        u.profile_image AS profileImage,

        st.name AS serviceType,
        st.name_ar AS serviceTypeAr,
        sc.name AS categoryName,

        p.average_rating AS averageRating,
        p.price AS price,
        p.price_type AS priceType,
        p.service_area_radius AS serviceAreaRadius,

        p.average_punctuality_rating AS averagePunctualityRating,
        p.average_commitment_rating AS averageCommitmentRating,
        p.average_quality_of_work_rating AS averageQualityOfWorkRating,

        l.area AS area,

        ST_Distance(
            CAST(l.coordinates AS geography),
            CAST(cl.consumer_coords AS geography)
        ) AS distanceMeters

    FROM providers p
    JOIN users u ON p.id = u.id
    JOIN locations l ON p.location_id = l.id
    JOIN service_types st ON p.service_type_id = st.id
    JOIN service_categories sc ON st.category_id = sc.id
    CROSS JOIN consumer_location cl
    WHERE p.verification_status = 'VERIFIED'
)

SELECT
    id,
    name,
    profileImage,

    serviceType,
    serviceTypeAr,
    categoryName,

    averageRating,
    price,
    priceType,
    serviceAreaRadius,

    averagePunctualityRating,
    averageCommitmentRating,
    averageQualityOfWorkRating,

    area,

    distanceMeters,
    (distanceMeters / 1000.0) AS distanceKm,
    CAST((distanceMeters / 1000.0 / 30.0) * 60 AS INTEGER) AS estimatedArrivalTime

FROM provider_distance
WHERE distanceMeters <= :radiusMeters
ORDER BY distanceMeters ASC
""",
                countQuery = """
SELECT COUNT(*)
FROM providers p
JOIN locations l ON p.location_id = l.id
CROSS JOIN (
    SELECT cl.coordinates AS consumer_coords
    FROM consumers c
    JOIN locations cl ON c.location_id = cl.id
    WHERE c.id = :consumerId
) cl
WHERE p.verification_status = 'VERIFIED'
AND ST_DWithin(
    CAST(l.coordinates AS geography),
    CAST(cl.consumer_coords AS geography),
    :radiusMeters
)
""",
                nativeQuery = true)
        Page<ProviderDistanceProjection> findTopRatedNearConsumer(
                @Param("consumerId") Long consumerId,
                @Param("radiusMeters") double radiusMeters,
                Pageable pageable
        );
}