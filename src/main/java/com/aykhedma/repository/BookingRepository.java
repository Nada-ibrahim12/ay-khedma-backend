package com.aykhedma.repository;

import com.aykhedma.model.booking.Booking;
import com.aykhedma.model.booking.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long>
{
    Page<Booking> findByConsumerId(Long consumerId, Pageable pageable);

    Page<Booking> findByProviderId(Long providerId, Pageable pageable);

    Page<Booking> findByConsumerIdAndStatus(Long consumerId, BookingStatus status, Pageable pageable);

    Page<Booking> findByProviderIdAndStatus(Long providerId, BookingStatus status, Pageable pageable);

    List<Booking> findByConsumerIdAndStatusAndRequestedDateAndRequestedStartTimeAfter
            (Long consumerId, BookingStatus status, LocalDate date, LocalTime time);

    List<Booking> findByProviderIdAndStatusAndRequestedDateAndRequestedStartTimeAfter
            (Long providerId, BookingStatus status, LocalDate date, LocalTime time);

    @Query("SELECT b FROM Booking b WHERE b.provider.id = :providerId AND b.requestedDate = :date")
    List<Booking> findProviderBookingsByDate(@Param("providerId") Long providerId,
                                             @Param("date") LocalDate date);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.provider.id = :providerId AND b.status = 'COMPLETED'")
    Long countCompletedBookingsByProvider(@Param("providerId") Long providerId);

    @Query("SELECT AVG(b.consumerRating) FROM Booking b WHERE b.provider.id = :providerId AND b.consumerRating IS NOT NULL")
    Double getAverageRatingForProvider(@Param("providerId") Long providerId);

    long countByProviderIdAndConsumerRatingIsNotNull(Long providerId);

    long countByConsumerIdAndProviderRatingIsNotNull(Long consumerId);

    List<Booking> findByConsumerIdAndProviderRatingIsNotNull(Long consumerId);

        @Modifying
        @Query("DELETE FROM Booking b WHERE b.consumer.id = :consumerId")
        void deleteByConsumerId(@Param("consumerId") Long consumerId);

        @Modifying
        @Query("DELETE FROM Booking b WHERE b.provider.id = :providerId")
        void deleteByProviderId(@Param("providerId") Long providerId);

    @Query("SELECT b FROM Booking b WHERE b.provider.id = :providerId AND b.status = 'COMPLETED' ORDER BY b.completedAt DESC")
    List<Booking> findRecentCompletedBookings(@Param("providerId") Long providerId, Pageable pageable);

    @Query(value = "SELECT * FROM bookings " +
            "WHERE provider_id = :providerId " +
            "AND requested_date = :requestedDate " +
            "AND id != :bookingId " +
            "AND " +
            "(" +
            "   (status = 'ACCEPTED' " +
            "    AND requested_start_time < :newEndTime " +
            "    AND :newStartTime < (requested_start_time + (estimated_duration * INTERVAL '1 minute'))) " +
            "   OR" +
            "   (status = 'PENDING' " +
            "    AND :newStartTime <= requested_start_time " +
            "    AND requested_start_time < :newEndTime)" +
            ") " +
            "ORDER BY status",
            nativeQuery = true)
    List<Booking> findConflictingBookings(@Param("providerId") Long providerId,
                                          @Param("bookingId") Long bookingId,
                                          @Param("requestedDate") LocalDate requestedDate,
                                          @Param("newStartTime") LocalTime newStartTime,
                                          @Param("newEndTime") LocalTime newEndTime);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Booking b " +
            "SET b.status = 'EXPIRED', b.expiredAt = :currentTimestamp " +
            "WHERE b.status = 'PENDING' " +
            "AND " +
            "(" +
                "b.requestedDate < :currentDate " +
                "OR (b.requestedDate = :currentDate AND b.requestedStartTime < :currentTime)" +
            ")")
    void expirePendingBookings(@Param("currentTimestamp") LocalDateTime currentTimestamp,
                               @Param("currentDate") LocalDate currentDate,
                               @Param("currentTime") LocalTime currentTime);

    @Modifying(clearAutomatically = true)
    @Query(value = "UPDATE Booking b " +
            "SET b.status = 'EXPIRED', b.expiredAt = :currentTimestamp " +
            "WHERE b.status = 'ACCEPTED' " +
            "AND " +
            "(" +
                "b.requestedDate < :currentDate " +
                "OR (b.requestedDate = :currentDate AND b.requestedStartTime < :currentTime)" +
            ")")
    void expireAcceptedBookings(@Param("currentTimestamp") LocalDateTime currentTimestamp,
                               @Param("currentDate") LocalDate currentDate,
                               @Param("currentTime") LocalTime currentTime);

    @Query(value = "SELECT " +
            "SUM(CASE WHEN status IN ('COMPLETED', 'ACCEPTED') THEN 1 ELSE 0 END) AS completed_and_accepted, " +
            "SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled " +
            "FROM bookings " +
            "WHERE provider_id = :providerId " +
            "AND requested_date BETWEEN " +
            "(date_trunc('week', CAST(:currentDate AS DATE)) - INTERVAL '2 days') " +
            "AND (date_trunc('week', CAST(:currentDate AS DATE)) + INTERVAL '5 days')",
            nativeQuery = true)
    Object findBookingStatsCurrentWeek(@Param("providerId") Long providerId,
                                       @Param("currentDate") LocalDate currentDate);

    @Query(value = "SELECT TO_CHAR(requested_date, 'Mon') AS month, " +
            "SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) AS completed, " +
            "SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelled " +
            "FROM bookings " +
            "WHERE provider_id = :providerId " +
            "AND requested_date >= date_trunc('month', CAST(:currentDate AS DATE) - INTERVAL '6 months') " +
            "AND requested_date < date_trunc('month', CAST(:currentDate AS DATE)) " +
            "GROUP BY TO_CHAR(requested_date, 'Mon'), date_trunc('month', requested_date) " +
            "ORDER BY date_trunc('month', requested_date)",
            nativeQuery = true)
    List<Object[]> findBookingStatsLastSixMonths(@Param("providerId") Long providerId,
                                                 @Param("currentDate") LocalDate currentDate);

    @Query(value = "SELECT * FROM bookings " +
            "WHERE (provider_id = :userId OR consumer_id = :userId) " +
            "AND status = 'ACCEPTED' " +
            "AND requested_date IN " +
            "(" +
            "   SELECT DISTINCT requested_date FROM bookings " +
                "WHERE (provider_id = :userId OR consumer_id = :userId) " +
                "AND status = 'ACCEPTED' " +
                "AND " +
                "(" +
                    "requested_date > :currentDate " +
                    "OR (requested_date = :currentDate AND requested_start_time > :currentTime)" +
                ") " +
                "ORDER BY requested_date ASC LIMIT 2" +
            ") " +
            "AND " +
            "(" +
                "requested_date > :currentDate " +
                "OR (requested_date = :currentDate AND requested_start_time > :currentTime)" +
            ") " +
            "ORDER BY requested_date, requested_start_time",
            nativeQuery = true)
    List<Booking> findUpcomingBookings(@Param("userId") Long userId,
                                       @Param("currentDate") LocalDate currentDate,
                                       @Param("currentTime") LocalTime currentTime);

    @Query("SELECT b FROM Booking b WHERE b.status = 'ACCEPTED' " +
            "AND (b.consumerRating IS NULL OR b.providerRating IS NULL) " +
            "AND (b.requestedDate < :date OR (b.requestedDate = :date AND b.requestedStartTime <= :time))")
    List<Booking> findBookingsNeedingRating(@Param("date") LocalDate date, @Param("time") LocalTime time);
}
