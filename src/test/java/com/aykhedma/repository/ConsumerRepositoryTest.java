package com.aykhedma.repository;

import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.UserType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("ConsumerRepository Tests")
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
