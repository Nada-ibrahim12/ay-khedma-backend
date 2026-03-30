package com.aykhedma.repository;

import com.aykhedma.model.booking.Booking;
import com.aykhedma.model.booking.BookingStatus;
import com.aykhedma.model.booking.Schedule;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.location.Location;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.service.ServiceCategory;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.service.PriceType;
import com.aykhedma.model.service.RiskLevel;
import com.aykhedma.model.user.UserType;
import com.aykhedma.model.user.VerificationStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test-postgresql")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("BookingRepository Tests")
class BookingRepositoryTest {
    @Autowired
    private BookingRepository bookingRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private ServiceType serviceType;
    private Provider provider;
    private Consumer consumer;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
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

        Location location = Location.builder()
                .latitude(30.0444)
                .longitude(31.2357)
                .address("Test Address")
                .city("Cairo")
                .build();
        entityManager.persist(location);

        Schedule schedule = Schedule.builder().build();
        entityManager.persist(schedule);

        provider = Provider.builder()
                .name("Provider User")
                .email("provider@test.com")
                .phoneNumber("01234567890")
                .password("hashedpassword")
                .role(UserType.PROVIDER)
                .verificationStatus(VerificationStatus.VERIFIED)
                .serviceType(serviceType)
                .nationalId("12345678901234")
                .price(1000.0)
                .priceType(PriceType.VISIT)
                .location(location)
                .schedule(schedule)
                .build();
        entityManager.persist(provider);

        consumer = Consumer.builder()
                .name("Consumer User")
                .email("consumer@test.com")
                .phoneNumber("01987654321")
                .password("hashedpassword")
                .role(UserType.CONSUMER)
                .build();
        entityManager.persist(consumer);
    }

    private Booking buildBooking(BookingStatus status, LocalDate date, LocalTime time) {
        return Booking.builder()
                .consumer(consumer)
                .provider(provider)
                .serviceType(serviceType)
                .requestedDate(date)
                .requestedStartTime(time)
                .estimatedDuration(60L)
                .status(status)
                .build();
    }

    @Nested
    @DisplayName("Find Conflicting Bookings Tests")
    class FindConflictingBookingsTest {
        @Test
        @DisplayName("Return Conflicting Accepted Booking")
        void conflictingAcceptedBookingTest() {
            LocalDate date = LocalDate.now();
            LocalTime time = LocalTime.of(10, 0);

            Booking existing = buildBooking(BookingStatus.ACCEPTED, date, time);
            bookingRepository.save(existing);

            LocalTime newStartTime = LocalTime.of(10, 30);
            LocalTime newEndTime = LocalTime.of(11, 30);

            List<Booking> conflicts = bookingRepository.findConflictingBookings(provider.getId(),
                    existing.getId() + 1L, date, newStartTime, newEndTime);

            assertThat(conflicts).isNotEmpty();
            assertThat(conflicts).anyMatch(b -> b.getId().equals(existing.getId()));
        }

        @Test
        @DisplayName("Return Conflicting Pending Booking")
        void conflictingPendingBookingTest() {
            LocalDate date = LocalDate.now();
            LocalTime time = LocalTime.of(11, 0);

            Booking existing = buildBooking(BookingStatus.PENDING, date, time);
            bookingRepository.save(existing);

            LocalTime newStartTime = LocalTime.of(10, 30);
            LocalTime newEndTime = LocalTime.of(11, 30);

            List<Booking> conflicts = bookingRepository.findConflictingBookings(provider.getId(),
                    existing.getId() + 1L, date, newStartTime, newEndTime);

            assertThat(conflicts).isNotEmpty();

            assertThat(conflicts).isNotEmpty();
            assertThat(conflicts).anyMatch(b -> b.getId().equals(existing.getId()));
        }
    }

    @Nested
    @DisplayName("Expire Pending Bookings Tests")
    class ExpirePendingBookingsTest {
        @Test
        @Transactional
        @Rollback
        @DisplayName("Expire Past Pending Bookings")
        void expirePastPendingBookingsTest() {
            LocalDate date = LocalDate.now();
            LocalTime time = LocalTime.now().minusHours(1);

            Booking expiring = buildBooking(BookingStatus.PENDING, date, time);
            bookingRepository.save(expiring);

            bookingRepository.expirePendingBookings(LocalDate.now(), LocalTime.now());
            entityManager.flush();
            entityManager.clear();

            Booking updated = bookingRepository.findById(expiring.getId()).orElseThrow();

            assertThat(updated.getStatus()).isEqualTo(BookingStatus.EXPIRED);
            assertThat(updated.getExpiredAt()).isNotNull();
        }

        @Test
        @Transactional
        @Rollback
        @DisplayName("Do Not Expire Future Pending Bookings")
        void doNotExpireFuturePendingBookingsTest() {
            LocalDate date = LocalDate.now();
            LocalTime time = LocalTime.now().plusHours(1);

            Booking pending = buildBooking(BookingStatus.PENDING, date, time);
            bookingRepository.save(pending);

            bookingRepository.expirePendingBookings(LocalDate.now(), LocalTime.now());
            entityManager.flush();
            entityManager.clear();

            Booking updated = bookingRepository.findById(pending.getId()).orElseThrow();

            assertThat(updated.getStatus()).isEqualTo(BookingStatus.PENDING);
            assertThat(updated.getExpiredAt()).isNull();
        }
    }
}
