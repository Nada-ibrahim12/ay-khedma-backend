package com.aykhedma.repository;

import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.user.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProviderRepository extends JpaRepository<Provider, Long> {

    Optional<Provider> findByEmail(String email);

    Optional<Provider> findByPhoneNumber(String phoneNumber);

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
            "AND ST_DWithin(l.coordinates, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326), :radius)",
            nativeQuery = true)
    List<Provider> findNearbyProviders(@Param("latitude") Double latitude,
                                       @Param("longitude") Double longitude,
                                       @Param("radius") Double radius,
                                       @Param("serviceTypeId") Long serviceTypeId);

    @Modifying
    @Query("UPDATE Provider p SET p.profileImage = :profileImage WHERE p.id = :providerId")
    void updateProfileImage(@Param("providerId") Long providerId, @Param("profileImage") String profileImage);

    List<Provider> findByServiceTypeId(Long serviceTypeId);
}