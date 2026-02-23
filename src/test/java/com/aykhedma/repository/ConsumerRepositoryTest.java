package com.aykhedma.repository;

import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.user.UserType;
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

    @Autowired
    private ProviderRepository providerRepository;

    private Consumer consumer;
    private Provider provider;

    @BeforeEach
    void setUp() {
        consumer = TestDataFactory.createConsumer(null);
        consumer.setEmail("unique" + System.currentTimeMillis() + "@test.com");
        consumer.setPhoneNumber("011" + (System.currentTimeMillis() % 1000000000));

        provider = TestDataFactory.createProvider(null);
        provider.setEmail("provider" + System.currentTimeMillis() + "@test.com");
        provider.setPhoneNumber("012" + (System.currentTimeMillis() % 1000000000));
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