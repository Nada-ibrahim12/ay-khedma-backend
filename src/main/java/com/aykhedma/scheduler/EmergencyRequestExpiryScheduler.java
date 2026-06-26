package com.aykhedma.scheduler;

import com.aykhedma.repository.EmergencyRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class EmergencyRequestExpiryScheduler
{
    private final EmergencyRequestRepository emergencyRequestRepository;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void expireEmergencyRequests ()
    {
        emergencyRequestRepository.expirePendingEmergencyRequests(LocalDateTime.now());
        emergencyRequestRepository.expireAcceptedEmergencyRequests(LocalDateTime.now());
    }
}
