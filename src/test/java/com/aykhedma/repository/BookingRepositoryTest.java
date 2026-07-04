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
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestEntityManager;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureTestEntityManager
@Transactional
@ActiveProfiles("test")
@DisplayName("BookingRepository Tests")
class BookingRepositoryTest
{
    @Autowired
    private BookingRepository bookingRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private ServiceType serviceType;
    private Provider provider;
    private Consumer consumer;

    @BeforeEach
    void setUp()
    {
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

    private Booking buildBooking(BookingStatus status, LocalDate date, LocalTime time)
    {
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
    class FindConflictingBookingsTest
    {
        @Test
        @DisplayName("Return Conflicting Accepted Booking")
        void conflictingAcceptedBookingTest()
        {
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
        void conflictingPendingBookingTest()
        {
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
    class ExpirePendingBookingsTest
    {
        @Test
        @Transactional
        @Rollback
        @DisplayName("Expire Past Pending Bookings")
        void expirePastPendingBookingsTest()
        {
            LocalDate date = LocalDate.now();
            LocalTime time = LocalTime.now().minusHours(1).withNano(0);

            Booking expiring = buildBooking(BookingStatus.PENDING, date, time);
            bookingRepository.save(expiring);

            bookingRepository.expirePendingBookings(LocalDateTime.now(), LocalDate.now(), LocalTime.now());
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
        void doNotExpireFuturePendingBookingsTest()
        {
            LocalDate date = LocalDate.now();
            LocalTime time = LocalTime.now().plusHours(1).withNano(0);

            Booking pending = buildBooking(BookingStatus.PENDING, date, time);
            bookingRepository.save(pending);

            bookingRepository.expirePendingBookings(LocalDateTime.now(), LocalDate.now(), LocalTime.now());
            entityManager.flush();
            entityManager.clear();

            Booking updated = bookingRepository.findById(pending.getId()).orElseThrow();

            assertThat(updated.getStatus()).isEqualTo(BookingStatus.PENDING);
            assertThat(updated.getExpiredAt()).isNull();
        }
    }

    @Nested
    @DisplayName("Expire Accepted Bookings Tests")
    class ExpireAcceptedBookingsTest
    {
        @Test
        @Transactional
        @Rollback
        @DisplayName("Expire Past Accepted Bookings")
        void expirePastAcceptedBookingsTest()
        {
            LocalDate date = LocalDate.now();
            LocalTime time = LocalTime.now().minusHours(1).withNano(0);

            Booking expiring = buildBooking(BookingStatus.ACCEPTED, date, time);
            bookingRepository.save(expiring);

            bookingRepository.expireAcceptedBookings(LocalDateTime.now(), LocalDate.now(), LocalTime.now());
            entityManager.flush();
            entityManager.clear();

            Booking updated = bookingRepository.findById(expiring.getId()).orElseThrow();

            assertThat(updated.getStatus()).isEqualTo(BookingStatus.EXPIRED);
            assertThat(updated.getExpiredAt()).isNotNull();
        }

        @Test
        @Transactional
        @Rollback
        @DisplayName("Do Not Expire Future Accepted Bookings")
        void doNotExpireFutureAcceptedBookingsTest()
        {
            LocalDate date = LocalDate.now();
            LocalTime time = LocalTime.now().plusHours(1).withNano(0);

            Booking accepted = buildBooking(BookingStatus.ACCEPTED, date, time);
            bookingRepository.save(accepted);

            bookingRepository.expireAcceptedBookings(LocalDateTime.now(), LocalDate.now(), LocalTime.now());
            entityManager.flush();
            entityManager.clear();

            Booking updated = bookingRepository.findById(accepted.getId()).orElseThrow();

            assertThat(updated.getStatus()).isEqualTo(BookingStatus.ACCEPTED);
            assertThat(updated.getExpiredAt()).isNull();
        }
    }

    @Nested
    @DisplayName("Find Booking Stats Current Week Tests")
    class FindBookingStatsCurrentWeekTest
    {
        @Test
        @DisplayName("Return Correct Stats For Current Week")
        void returnCorrectStatsForCurrentWeekTest()
        {
            LocalDate currentDate = LocalDate.of(2026, 7, 6);

            Booking acceptedMon = buildBooking(BookingStatus.ACCEPTED, LocalDate.of(2026, 7, 6), LocalTime.of(9, 0));
            bookingRepository.save(acceptedMon);

            Booking completedWed = buildBooking(BookingStatus.COMPLETED, LocalDate.of(2026, 7, 8), LocalTime.of(10, 0));
            bookingRepository.save(completedWed);

            Booking cancelledFri = buildBooking(BookingStatus.CANCELLED, LocalDate.of(2026, 7, 10), LocalTime.of(11, 0));
            bookingRepository.save(cancelledFri);

            Booking acceptedSun = buildBooking(BookingStatus.ACCEPTED, LocalDate.of(2026, 7, 10), LocalTime.of(14, 0));
            bookingRepository.save(acceptedSun);

            Booking outside = buildBooking(BookingStatus.ACCEPTED, LocalDate.of(2026, 6, 29), LocalTime.of(8, 0));
            bookingRepository.save(outside);

            Object result = bookingRepository.findBookingStatsCurrentWeek(provider.getId(), currentDate);
            Object[] stats = (Object[]) result;

            assertThat(stats[0]).isEqualTo(3L);
            assertThat(stats[1]).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("Find Booking Stats Last Six Months Tests")
    class FindBookingStatsLastSixMonthsTest
    {
        @Test
        @DisplayName("Return Correct Monthly Stats For Last Six Months")
        void returnCorrectMonthlyStatsForLastSixMonthsTest()
        {
            LocalDate currentDate = LocalDate.of(2026, 7, 1);

            Booking janComplete1 = buildBooking(BookingStatus.COMPLETED, LocalDate.of(2026, 1, 5), LocalTime.of(9, 0));
            Booking janComplete2 = buildBooking(BookingStatus.COMPLETED, LocalDate.of(2026, 1, 15), LocalTime.of(10, 0));
            Booking janCancelled = buildBooking(BookingStatus.CANCELLED, LocalDate.of(2026, 1, 20), LocalTime.of(11, 0));
            bookingRepository.save(janComplete1);
            bookingRepository.save(janComplete2);
            bookingRepository.save(janCancelled);

            Booking febComplete = buildBooking(BookingStatus.COMPLETED, LocalDate.of(2026, 2, 10), LocalTime.of(9, 0));
            Booking febCancelled1 = buildBooking(BookingStatus.CANCELLED, LocalDate.of(2026, 2, 12), LocalTime.of(10, 0));
            Booking febCancelled2 = buildBooking(BookingStatus.CANCELLED, LocalDate.of(2026, 2, 14), LocalTime.of(11, 0));
            bookingRepository.save(febComplete);
            bookingRepository.save(febCancelled1);
            bookingRepository.save(febCancelled2);

            Booking marComplete = buildBooking(BookingStatus.COMPLETED, LocalDate.of(2026, 3, 5), LocalTime.of(9, 0));
            bookingRepository.save(marComplete);

            Booking aprCancelled = buildBooking(BookingStatus.CANCELLED, LocalDate.of(2026, 4, 1), LocalTime.of(10, 0));
            bookingRepository.save(aprCancelled);

            Booking junComplete1 = buildBooking(BookingStatus.COMPLETED, LocalDate.of(2026, 6, 5), LocalTime.of(9, 0));
            Booking junComplete2 = buildBooking(BookingStatus.COMPLETED, LocalDate.of(2026, 6, 15), LocalTime.of(10, 0));
            Booking junCancelled = buildBooking(BookingStatus.CANCELLED, LocalDate.of(2026, 6, 20), LocalTime.of(11, 0));
            bookingRepository.save(junComplete1);
            bookingRepository.save(junComplete2);
            bookingRepository.save(junCancelled);

            Booking julComplete = buildBooking(BookingStatus.COMPLETED, LocalDate.of(2026, 7, 1), LocalTime.of(12, 0));
            bookingRepository.save(julComplete);

            List<Object[]> results = bookingRepository.findBookingStatsLastSixMonths(provider.getId(), currentDate);

            assertThat(results).hasSize(6);

            assertThat(results.get(0)[0]).isEqualTo("Jan");
            assertThat(results.get(0)[1]).isEqualTo(2L);
            assertThat(results.get(0)[2]).isEqualTo(1L);

            assertThat(results.get(1)[0]).isEqualTo("Feb");
            assertThat(results.get(1)[1]).isEqualTo(1L);
            assertThat(results.get(1)[2]).isEqualTo(2L);

            assertThat(results.get(2)[0]).isEqualTo("Mar");
            assertThat(results.get(2)[1]).isEqualTo(1L);
            assertThat(results.get(2)[2]).isEqualTo(0L);

            assertThat(results.get(3)[0]).isEqualTo("Apr");
            assertThat(results.get(3)[1]).isEqualTo(0L);
            assertThat(results.get(3)[2]).isEqualTo(1L);

            assertThat(results.get(4)[0]).isEqualTo("May");
            assertThat(results.get(4)[1]).isEqualTo(0L);
            assertThat(results.get(4)[2]).isEqualTo(0L);

            assertThat(results.get(5)[0]).isEqualTo("Jun");
            assertThat(results.get(5)[1]).isEqualTo(2L);
            assertThat(results.get(5)[2]).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("Find Upcoming Bookings Tests")
    class FindUpcomingBookingsTest
    {
        @Test
        @DisplayName("Return Next Two Distinct Dates Of Accepted Bookings")
        void returnNextTwoDistinctDatesOfAcceptedBookingsTest()
        {
            LocalDate currentDate = LocalDate.of(2026, 7, 1);
            LocalTime currentTime = LocalTime.of(10, 0);

            Booking future1a = buildBooking(BookingStatus.ACCEPTED, LocalDate.of(2026, 7, 2), LocalTime.of(9, 0));
            Booking future1b = buildBooking(BookingStatus.ACCEPTED, LocalDate.of(2026, 7, 2), LocalTime.of(11, 0));
            bookingRepository.save(future1a);
            bookingRepository.save(future1b);

            Booking future2 = buildBooking(BookingStatus.ACCEPTED, LocalDate.of(2026, 7, 3), LocalTime.of(10, 0));
            bookingRepository.save(future2);

            Booking future3 = buildBooking(BookingStatus.ACCEPTED, LocalDate.of(2026, 7, 5), LocalTime.of(14, 0));
            bookingRepository.save(future3);

            Booking past = buildBooking(BookingStatus.ACCEPTED, LocalDate.of(2026, 6, 30), LocalTime.of(10, 0));
            bookingRepository.save(past);

            Booking pendingFuture = buildBooking(BookingStatus.PENDING, LocalDate.of(2026, 7, 6), LocalTime.of(9, 0));
            bookingRepository.save(pendingFuture);

            List<Booking> upcoming = bookingRepository.findUpcomingBookings(provider.getId(), currentDate, currentTime);

            assertThat(upcoming).hasSize(3);

            assertThat(upcoming.get(0).getRequestedDate()).isEqualTo(LocalDate.of(2026, 7, 2));
            assertThat(upcoming.get(0).getRequestedStartTime()).isEqualTo(LocalTime.of(9, 0));

            assertThat(upcoming.get(1).getRequestedDate()).isEqualTo(LocalDate.of(2026, 7, 2));
            assertThat(upcoming.get(1).getRequestedStartTime()).isEqualTo(LocalTime.of(11, 0));

            assertThat(upcoming.get(2).getRequestedDate()).isEqualTo(LocalDate.of(2026, 7, 3));
            assertThat(upcoming.get(2).getRequestedStartTime()).isEqualTo(LocalTime.of(10, 0));

            assertThat(upcoming).noneMatch(b -> b.getRequestedDate().equals(LocalDate.of(2026, 7, 5)));
        }

        @Test
        @DisplayName("Return Bookings For Same Day Future Time")
        void returnBookingsForSameDayFutureTimeTest()
        {
            LocalDate currentDate = LocalDate.of(2026, 7, 1);
            LocalTime currentTime = LocalTime.of(10, 0);

            Booking sameDay1 = buildBooking(BookingStatus.ACCEPTED, currentDate, LocalTime.of(11, 0));
            Booking sameDay2 = buildBooking(BookingStatus.ACCEPTED, currentDate, LocalTime.of(14, 0));
            bookingRepository.save(sameDay1);
            bookingRepository.save(sameDay2);

            Booking sameDayPast = buildBooking(BookingStatus.ACCEPTED, currentDate, LocalTime.of(9, 0));
            bookingRepository.save(sameDayPast);

            Booking nextDay = buildBooking(BookingStatus.ACCEPTED, LocalDate.of(2026, 7, 2), LocalTime.of(8, 0));
            bookingRepository.save(nextDay);


            List<Booking> upcoming = bookingRepository.findUpcomingBookings(provider.getId(), currentDate, currentTime);

            assertThat(upcoming).hasSize(3);
            assertThat(upcoming).extracting("requestedStartTime").containsExactly(
                    LocalTime.of(11, 0), LocalTime.of(14, 0), LocalTime.of(8, 0)
            );
            assertThat(upcoming).noneMatch(b -> b.getRequestedStartTime().equals(LocalTime.of(9, 0)));
        }

        @Test
        @DisplayName("Return Empty When No Upcoming Accepted Bookings")
        void returnEmptyWhenNoUpcomingAcceptedBookingsTest()
        {
            LocalDate currentDate = LocalDate.of(2026, 7, 1);
            LocalTime currentTime = LocalTime.of(10, 0);

            Booking pastAccepted = buildBooking(BookingStatus.ACCEPTED, LocalDate.of(2026, 6, 30), LocalTime.of(10, 0));
            Booking pendingFuture = buildBooking(BookingStatus.PENDING, LocalDate.of(2026, 7, 2), LocalTime.of(10, 0));
            bookingRepository.save(pastAccepted);
            bookingRepository.save(pendingFuture);

            List<Booking> upcoming = bookingRepository.findUpcomingBookings(provider.getId(), currentDate, currentTime);

            assertThat(upcoming).isEmpty();
        }
    }
}
