package com.aykhedma.repository;

import com.aykhedma.model.booking.Schedule;
import com.aykhedma.model.location.Location;
import com.aykhedma.model.rating.InteractionRating;
import com.aykhedma.model.service.PriceType;
import com.aykhedma.model.service.RiskLevel;
import com.aykhedma.model.service.ServiceCategory;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.user.UserType;
import com.aykhedma.model.user.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureTestEntityManager
@Transactional
@ActiveProfiles("test")
@DisplayName("InteractionRatingRepository Tests")
class InteractionRatingRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private InteractionRatingRepository repository;

    private Consumer consumer;
    private Provider provider;

    @BeforeEach
    void setUp() {
        String uniqueSuffix = String.valueOf(System.nanoTime());

        // Persist consumer with all required fields
        consumer = Consumer.builder()
                .name("Consumer Rating Test")
                .email("consumer.rating" + uniqueSuffix + "@test.com")
                .phoneNumber("010" + uniqueSuffix.substring(uniqueSuffix.length() - 8))
                .password("encodedPassword")
                .role(UserType.CONSUMER)
                .enabled(true)
                .credentialsNonExpired(true)
                .build();
        consumer = entityManager.persist(consumer);

        // Persist ServiceCategory -> ServiceType (required by Provider)
        ServiceCategory category = ServiceCategory.builder()
                .name("Test Services " + uniqueSuffix)
                .build();
        entityManager.persist(category);

        ServiceType serviceType = ServiceType.builder()
                .name("Test Type " + uniqueSuffix)
                .category(category)
                .riskLevel(RiskLevel.LOW)
                .defaultPriceType(PriceType.HOUR)
                .basePrice(100.0)
                .build();
        entityManager.persist(serviceType);

        // Persist provider with all required fields
        Location location = Location.builder()
                .latitude(30.0444)
                .longitude(31.2357)
                .address("123 Test Street")
                .area("Maadi")
                .city("Cairo")
                .build();
        Schedule schedule = Schedule.builder().build();

        provider = Provider.builder()
                .name("Provider Rating Test")
                .email("provider.rating" + uniqueSuffix + "@test.com")
                .phoneNumber("012" + uniqueSuffix.substring(uniqueSuffix.length() - 8))
                .password("encodedPassword")
                .role(UserType.PROVIDER)
                .enabled(true)
                .credentialsNonExpired(true)
                .nationalId(String.format("%014d", Math.floorMod(System.nanoTime(), 100_000_000_000_000L)))
                .serviceType(serviceType)
                .location(location)
                .schedule(schedule)
                .verificationStatus(VerificationStatus.VERIFIED)
                .price(100.0)
                .priceType(PriceType.HOUR)
                .emergencyEnabled(false)
                .build();
        provider = entityManager.persist(provider);
        entityManager.flush();
    }

    @Test
    @DisplayName("findByProviderId should return provider ratings")
    void findByProviderId_returnsRatings() {
        InteractionRating rating = InteractionRating.builder()
                .consumer(consumer)
                .provider(provider)
                .rating(4.5)
                .comment("Great provider profile!")
                .build();
        entityManager.persistAndFlush(rating);

        List<InteractionRating> ratings = repository.findByProviderId(provider.getId());

        assertThat(ratings).hasSize(1);
        assertThat(ratings.get(0).getRating()).isEqualTo(4.5);
        assertThat(ratings.get(0).getComment()).isEqualTo("Great provider profile!");
    }

    @Test
    @DisplayName("findByConsumerId should return consumer ratings")
    void findByConsumerId_returnsRatings() {
        InteractionRating rating = InteractionRating.builder()
                .consumer(consumer)
                .provider(provider)
                .rating(5.0)
                .comment("Excellent interaction!")
                .build();
        entityManager.persistAndFlush(rating);

        List<InteractionRating> ratings = repository.findByConsumerId(consumer.getId());

        assertThat(ratings).hasSize(1);
        assertThat(ratings.get(0).getRating()).isEqualTo(5.0);
        assertThat(ratings.get(0).getComment()).isEqualTo("Excellent interaction!");
    }

    @Test
    @DisplayName("deleteByConsumerId should remove consumer ratings")
    void deleteByConsumerId_removesRatings() {
        InteractionRating rating = InteractionRating.builder()
                .consumer(consumer)
                .provider(provider)
                .rating(4.0)
                .comment("Good service")
                .build();
        entityManager.persistAndFlush(rating);

        repository.deleteByConsumerId(consumer.getId());
        entityManager.clear(); // Clear persistence context to see changes from database

        List<InteractionRating> ratings = repository.findByConsumerId(consumer.getId());
        assertThat(ratings).isEmpty();
    }

    @Test
    @DisplayName("deleteByProviderId should remove provider ratings")
    void deleteByProviderId_removesRatings() {
        InteractionRating rating = InteractionRating.builder()
                .consumer(consumer)
                .provider(provider)
                .rating(3.0)
                .comment("Average service")
                .build();
        entityManager.persistAndFlush(rating);

        repository.deleteByProviderId(provider.getId());
        entityManager.clear();

        List<InteractionRating> ratings = repository.findByProviderId(provider.getId());
        assertThat(ratings).isEmpty();
    }
}
