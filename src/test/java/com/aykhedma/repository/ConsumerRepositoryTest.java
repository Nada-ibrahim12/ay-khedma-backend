package com.aykhedma.repository;
import java.util.List;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.user.UserType;
import com.aykhedma.model.service.PriceType;
import com.aykhedma.model.service.RiskLevel;
import com.aykhedma.model.service.ServiceCategory;
import com.aykhedma.model.service.ServiceType;
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
@DisplayName("Consumer Repository Tests")
class ConsumerRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ConsumerRepository consumerRepository;

    // ═══════════════════════════════════════════════════════
    // findByEmail / findByPhoneNumber
    // ═══════════════════════════════════════════════════════
    @Test
    @DisplayName("findByEmail() should return consumer when email exists")
    void findByEmail_existingEmail_returnsConsumer() {
        Consumer consumer = Consumer.builder()
                .name("Consumer One")
                .email("consumer1@test.com")
                .phoneNumber("01022222222")
                .password("encodedPassword")
                .role(UserType.CONSUMER)
                .enabled(true)
                .credentialsNonExpired(true)
                .totalBookings(0)
                .build();
        entityManager.persistAndFlush(consumer);

        Optional<Consumer> result = consumerRepository.findByEmail("consumer1@test.com");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Consumer One");
    }

    @Test
    @DisplayName("findByPhoneNumber() should return consumer when phone exists")
    void findByPhoneNumber_existingPhone_returnsConsumer() {
        Consumer consumer = Consumer.builder()
                .name("Consumer Two")
                .email("consumer2@test.com")
                .phoneNumber("01033333333")
                .password("encodedPassword")
                .role(UserType.CONSUMER)
                .enabled(true)
                .credentialsNonExpired(true)
                .totalBookings(0)
                .build();
        entityManager.persistAndFlush(consumer);

        Optional<Consumer> result = consumerRepository.findByPhoneNumber("01033333333");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("consumer2@test.com");
    }

    // ═══════════════════════════════════════════════════════
    // findTopRatedConsumers (custom @Query)
    // ═══════════════════════════════════════════════════════
    @Test
    @DisplayName("findTopRatedConsumers() should return consumers above minimum rating")
    void findTopRatedConsumers_aboveThreshold_returnsMatching() {
        Consumer highRated = Consumer.builder()
                .name("High Rated")
                .email("high@test.com")
                .phoneNumber("01044444444")
                .password("encodedPassword")
                .role(UserType.CONSUMER)
                .enabled(true)
                .credentialsNonExpired(true)
                .totalBookings(5)
                .averageRating(4.5)
                .build();
        Consumer lowRated = Consumer.builder()
                .name("Low Rated")
                .email("low@test.com")
                .phoneNumber("01055555555")
                .password("encodedPassword")
                .role(UserType.CONSUMER)
                .enabled(true)
                .credentialsNonExpired(true)
                .totalBookings(2)
                .averageRating(2.0)
                .build();
        entityManager.persistAndFlush(highRated);
        entityManager.persistAndFlush(lowRated);

        List<Consumer> topRated = consumerRepository.findTopRatedConsumers(4.0);

        assertThat(topRated).hasSize(1);
        assertThat(topRated.get(0).getName()).isEqualTo("High Rated");
    }

    @Test
    @DisplayName("findTopRatedConsumers() should return empty when no consumers meet threshold")
    void findTopRatedConsumers_noneAboveThreshold_returnsEmpty() {
        Consumer consumer = Consumer.builder()
                .name("Average")
                .email("avg@test.com")
                .phoneNumber("01066666666")
                .password("encodedPassword")
                .role(UserType.CONSUMER)
                .enabled(true)
                .credentialsNonExpired(true)
                .totalBookings(1)
                .averageRating(3.0)
                .build();
        entityManager.persistAndFlush(consumer);

        List<Consumer> topRated = consumerRepository.findTopRatedConsumers(4.5);

        assertThat(topRated).isEmpty();
    }
}
    @Autowired
    private ProviderRepository providerRepository;

    private Consumer consumer;
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

        consumer = TestDataFactory.createConsumer(null);
        consumer.setEmail("unique" + System.currentTimeMillis() + "@test.com");
        long consumerSuffix = Math.floorMod(System.nanoTime(), 1_000_000_000L);
        consumer.setPhoneNumber("01" + String.format("%09d", consumerSuffix));

        provider = TestDataFactory.createProvider(null);
        provider.setEmail("provider" + System.currentTimeMillis() + "@test.com");
        long providerSuffix = Math.floorMod(System.nanoTime() + 1, 1_000_000_000L);
        provider.setPhoneNumber("01" + String.format("%09d", providerSuffix));
        provider.setServiceType(serviceType);
    }

    @Test
    @DisplayName("Should save and find consumer by ID")
    void saveAndFindById() {
        // Act
        Consumer savedConsumer = consumerRepository.save(consumer);
        entityManager.flush();

        Optional<Consumer> found = consumerRepository.findById(savedConsumer.getId());

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo(consumer.getName());
        assertThat(found.get().getEmail()).isEqualTo(consumer.getEmail());
        assertThat(found.get().getRole()).isEqualTo(UserType.CONSUMER);
    }

    @Test
    @DisplayName("Should update profile image")
    void updateProfileImage() {
        // Arrange
        Consumer savedConsumer = consumerRepository.save(consumer);
        entityManager.flush();
        entityManager.clear();

        String newImageUrl = "https://test.com/new-image.jpg";

        // Act
        int updatedCount = consumerRepository.updateProfileImage(savedConsumer.getId(), newImageUrl);
        entityManager.flush();
        entityManager.clear();

        // Assert
        assertThat(updatedCount).isEqualTo(1);
        Consumer updated = entityManager.find(Consumer.class, savedConsumer.getId());
        assertThat(updated.getProfileImage()).isEqualTo(newImageUrl);
    }

    @Test
    @DisplayName("Should check if provider is saved by consumer")
    void isProviderSavedNative() {
        // Arrange
        Consumer savedConsumer = consumerRepository.save(consumer);
        Provider savedProvider = providerRepository.save(provider);
        entityManager.flush();

        savedConsumer.getSavedProviders().add(savedProvider);
        consumerRepository.save(savedConsumer);
        entityManager.flush();
        entityManager.clear();

        // Act
        boolean isSaved = consumerRepository.isProviderSavedNative(
                savedConsumer.getId(),
                savedProvider.getId()
        );

        // Assert
        assertThat(isSaved).isTrue();
    }

    @Test
    @DisplayName("Should insert saved provider")
    void insertSavedProvider() {
        // Arrange
        Consumer savedConsumer = consumerRepository.save(consumer);
        Provider savedProvider = providerRepository.save(provider);
        entityManager.flush();

        // Act
        consumerRepository.insertSavedProvider(savedConsumer.getId(), savedProvider.getId());
        entityManager.flush();
        entityManager.clear();

        // Assert
        Consumer consumerWithProvider = entityManager.find(Consumer.class, savedConsumer.getId());
        assertThat(consumerWithProvider.getSavedProviders())
                .isNotEmpty()
                .extracting("id")
                .contains(savedProvider.getId());
    }

    @Test
    @DisplayName("Should delete saved provider")
    void deleteSavedProvider() {
        // Arrange
        Consumer savedConsumer = consumerRepository.save(consumer);
        Provider savedProvider = providerRepository.save(provider);
        entityManager.flush();

        savedConsumer.getSavedProviders().add(savedProvider);
        consumerRepository.save(savedConsumer);
        entityManager.flush();
        entityManager.clear();

        // Act
        int deletedCount = consumerRepository.deleteSavedProvider(
                savedConsumer.getId(),
                savedProvider.getId()
        );
        entityManager.flush();
        entityManager.clear();

        // Assert
        assertThat(deletedCount).isEqualTo(1);

        Consumer consumerWithoutProvider = entityManager.find(Consumer.class, savedConsumer.getId());
        assertThat(consumerWithoutProvider.getSavedProviders()).isEmpty();
    }
}
