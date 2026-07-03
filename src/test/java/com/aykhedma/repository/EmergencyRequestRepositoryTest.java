package com.aykhedma.repository;

import com.aykhedma.model.emergency.EmergencyRequest;
import com.aykhedma.model.emergency.EmergencyRequestStatus;
import com.aykhedma.model.location.Location;
import com.aykhedma.model.service.PriceType;
import com.aykhedma.model.service.RiskLevel;
import com.aykhedma.model.service.ServiceCategory;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.UserType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestEntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureTestEntityManager
@Transactional
@DisplayName("EmergencyRequestRepository Tests")
class EmergencyRequestRepositoryTest
{
    @Autowired
    private EmergencyRequestRepository emergencyRequestRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private ServiceType serviceType;
    private Consumer consumer;
    private Location location;

    @BeforeEach
    void setUp()
    {
        emergencyRequestRepository.deleteAll();
        entityManager.flush();

        ServiceCategory category = ServiceCategory.builder()
                .name("Home Cooking Services")
                .build();
        entityManager.persist(category);

        serviceType = ServiceType.builder()
                .name("Cooking")
                .category(category)
                .riskLevel(RiskLevel.LOW)
                .basePrice(500.0)
                .defaultPriceType(PriceType.VISIT)
                .estimatedDuration(120)
                .build();
        entityManager.persist(serviceType);

        location = Location.builder()
                .latitude(30.0444)
                .longitude(31.2357)
                .address("Test Address")
                .city("Cairo")
                .build();
        entityManager.persist(location);

        consumer = Consumer.builder()
                .name("Consumer User")
                .email("consumer@test.com")
                .phoneNumber("01987654321")
                .password("hashedpassword")
                .role(UserType.CONSUMER)
                .build();
        entityManager.persist(consumer);
    }

    private EmergencyRequest buildEmergencyRequest(EmergencyRequestStatus status)
    {
        return EmergencyRequest.builder()
                .consumer(consumer)
                .serviceType(serviceType)
                .location(location)
                .price(500.0)
                .status(status)
                .build();
    }

    @Nested
    @DisplayName("Expire Pending Emergency Requests Tests")
    class ExpirePendingEmergencyRequestsTest
    {
        @Test
        @Transactional
        @Rollback
        @DisplayName("Expire Old Waiting Acceptance Requests")
        void expireOldWaitingAcceptanceRequestsTest()
        {
            EmergencyRequest expiring = buildEmergencyRequest(EmergencyRequestStatus.WAITING_ACCEPTANCE);
            emergencyRequestRepository.save(expiring);

            emergencyRequestRepository.expirePendingEmergencyRequests(LocalDateTime.now().plusHours(2).withNano(0));
            entityManager.flush();
            entityManager.clear();

            EmergencyRequest updated = emergencyRequestRepository.findById(expiring.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(EmergencyRequestStatus.EXPIRED);
        }

        @Test
        @Transactional
        @Rollback
        @DisplayName("Do Not Expire Recent Waiting Acceptance Requests")
        void doNotExpireRecentWaitingAcceptanceRequestsTest()
        {
            EmergencyRequest recent = buildEmergencyRequest(EmergencyRequestStatus.WAITING_ACCEPTANCE);
            emergencyRequestRepository.save(recent);

            emergencyRequestRepository.expirePendingEmergencyRequests(LocalDateTime.now());
            entityManager.flush();
            entityManager.clear();

            EmergencyRequest updated = emergencyRequestRepository.findById(recent.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(EmergencyRequestStatus.WAITING_ACCEPTANCE);
        }
    }

    @Nested
    @DisplayName("Expire Accepted Emergency Requests Tests")
    class ExpireAcceptedEmergencyRequestsTest
    {
        @Test
        @Transactional
        @Rollback
        @DisplayName("Expire Old Accepted Requests")
        void expireOldAcceptedRequestsTest()
        {
            EmergencyRequest expiring = buildEmergencyRequest(EmergencyRequestStatus.ACCEPTED);
            emergencyRequestRepository.save(expiring);

            emergencyRequestRepository.expireAcceptedEmergencyRequests(LocalDateTime.now().plusDays(2).withNano(0));
            entityManager.flush();
            entityManager.clear();

            EmergencyRequest updated = emergencyRequestRepository.findById(expiring.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(EmergencyRequestStatus.EXPIRED);
        }

        @Test
        @Transactional
        @Rollback
        @DisplayName("Do Not Expire Recent Accepted Requests")
        void doNotExpireRecentAcceptedRequestsTest()
        {
            EmergencyRequest recent = buildEmergencyRequest(EmergencyRequestStatus.ACCEPTED);
            emergencyRequestRepository.save(recent);

            emergencyRequestRepository.expireAcceptedEmergencyRequests(LocalDateTime.now());
            entityManager.flush();
            entityManager.clear();

            EmergencyRequest updated = emergencyRequestRepository.findById(recent.getId()).orElseThrow();
            assertThat(updated.getStatus()).isEqualTo(EmergencyRequestStatus.ACCEPTED);
        }
    }
}
