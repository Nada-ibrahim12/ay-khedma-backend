package com.aykhedma.repository;

import com.aykhedma.model.location.Location;
import com.aykhedma.model.service.PriceType;
import com.aykhedma.model.service.RiskLevel;
import com.aykhedma.model.service.ServiceCategory;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.user.UserType;
import com.aykhedma.model.user.VerificationStatus;
import com.aykhedma.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("Provider Repository Tests")
class ProviderRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProviderRepository providerRepository;

    private Provider provider;
    private ServiceType serviceType;

    @BeforeEach
    void setUp() {
        String uniqueSuffix = String.valueOf(System.nanoTime());
        ServiceCategory category = ServiceCategory.builder()
                .name("Home Services " + uniqueSuffix)
                .build();
        entityManager.persist(category);

        serviceType = ServiceType.builder()
                .name("Plumbing " + uniqueSuffix)
                .category(category)
                .riskLevel(RiskLevel.LOW)
                .defaultPriceType(PriceType.HOUR)
                .basePrice(100.0)
                .build();
        entityManager.persist(serviceType);

        provider = TestDataFactory.createProvider(null);
        provider.setEmail("provider" + System.currentTimeMillis() + "@test.com");
        long providerSuffix = Math.floorMod(System.nanoTime(), 1_000_000_000L);
        provider.setPhoneNumber("01" + String.format("%09d", providerSuffix));
        provider.setNationalId(String.format("%014d", Math.floorMod(System.nanoTime(), 100_000_000_000_000L)));
        provider.setServiceType(serviceType);
        provider.setVerificationStatus(VerificationStatus.VERIFIED);
        provider.setRole(UserType.PROVIDER);
    }

    @Test
    @DisplayName("Should save and find provider by ID")
    void saveAndFindById() {
        Provider savedProvider = providerRepository.save(provider);
        entityManager.flush();

        Optional<Provider> found = providerRepository.findById(savedProvider.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo(provider.getName());
        assertThat(found.get().getEmail()).isEqualTo(provider.getEmail());
        assertThat(found.get().getRole()).isEqualTo(UserType.PROVIDER);
    }

    @Test
    @DisplayName("Should update profile image")
    void updateProfileImage() {
        Provider savedProvider = providerRepository.save(provider);
        entityManager.flush();
        entityManager.clear();

        String newImageUrl = "https://test.com/new-provider-image.jpg";

        providerRepository.updateProfileImage(savedProvider.getId(), newImageUrl);
        entityManager.flush();
        entityManager.clear();

        Provider updated = entityManager.find(Provider.class, savedProvider.getId());
        assertThat(updated.getProfileImage()).isEqualTo(newImageUrl);
    }

    @Test
    @DisplayName("Should find provider by email")
    void findByEmail() {
        Provider savedProvider = providerRepository.save(provider);
        entityManager.flush();

        Optional<Provider> found = providerRepository.findByEmail(savedProvider.getEmail());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(savedProvider.getId());
    }
}
