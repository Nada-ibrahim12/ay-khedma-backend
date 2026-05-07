package com.aykhedma.repository;

import com.aykhedma.model.emergency.EmergencyRequest;
import com.aykhedma.model.emergency.EmergencyRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EmergencyRequestRepository extends JpaRepository<EmergencyRequest, Long>
{
    List<EmergencyRequest> findByConsumerId(Long consumerId);

    List<EmergencyRequest> findByStatus(EmergencyRequestStatus status);

    @Query("SELECT er FROM EmergencyRequest er WHERE er.selectedProvider.id = :providerId")
    List<EmergencyRequest> findBySelectedProviderId(@Param("providerId") Long providerId);

    @Query("SELECT er FROM EmergencyRequest er JOIN er.providerResponses pr WHERE pr.provider.id = :providerId")
    List<EmergencyRequest> findRequestsWithProviderResponse(@Param("providerId") Long providerId);

    EmergencyRequest findTopByConsumerIdAndStatusInOrderByCreatedAtDesc(Long consumerId, List<EmergencyRequestStatus> statuses);

    List<EmergencyRequest> findByConsumerIdAndStatusOrderByCreatedAtDesc(Long consumerId, EmergencyRequestStatus status);

    List<EmergencyRequest> findBySelectedProviderIdAndStatusOrderByCreatedAtDesc(Long providerId, EmergencyRequestStatus status);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE EmergencyRequest er " +
            "SET er.status = 'EXPIRED' " +
            "WHERE er.status = 'WAITING_ACCEPTANCE' " +
            "AND er.createdAt + 1 hour < :currentTimestamp ")
    void expireEmergencyRequests(@Param("currentTimestamp")LocalDateTime currentTimestamp);
}
