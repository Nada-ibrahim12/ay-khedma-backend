package com.aykhedma.repository;

import com.aykhedma.model.emergency.ProviderResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProviderResponseRepository extends JpaRepository<ProviderResponse, Long> {

    List<ProviderResponse> findByEmergencyRequestId(Long emergencyRequestId);

    List<ProviderResponse> findByProviderId(Long providerId);

    @Query("SELECT pr FROM ProviderResponse pr WHERE pr.emergencyRequest.id = :requestId AND pr.responseType = 'ACCEPTED_OFFER'")
    List<ProviderResponse> findAcceptedResponsesForRequest(@Param("requestId") Long requestId);

    boolean existsByEmergencyRequestIdAndProviderId(Long emergencyRequestId, Long providerId);
}