package com.aykhedma.repository;

import com.aykhedma.model.booking.Booking;
import com.aykhedma.model.booking.BookingStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByConsumerId(Long consumerId);

    List<Booking> findByProviderId(Long providerId);

    List<Booking> findByConsumerIdAndStatus(Long consumerId, BookingStatus status);

    List<Booking> findByProviderIdAndStatus(Long providerId, BookingStatus status);

    @Query("SELECT b FROM Booking b WHERE b.provider.id = :providerId AND b.requestedDate = :date")
    List<Booking> findProviderBookingsByDate(@Param("providerId") Long providerId,
                                             @Param("date") LocalDate date);

    @Query("SELECT b FROM Booking b WHERE b.provider.id = :providerId " +
            "AND b.status IN ('PENDING', 'ACCEPTED') " +
            "AND b.requestedDate >= :startDate")
    List<Booking> findUpcomingBookings(@Param("providerId") Long providerId,
                                       @Param("startDate") LocalDate startDate);

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.provider.id = :providerId AND b.status = 'COMPLETED'")
    Long countCompletedBookingsByProvider(@Param("providerId") Long providerId);

    @Query("SELECT AVG(b.consumerRating) FROM Booking b WHERE b.provider.id = :providerId AND b.consumerRating IS NOT NULL")
    Double getAverageRatingForProvider(@Param("providerId") Long providerId);

    @Query("SELECT b FROM Booking b WHERE b.provider.id = :providerId AND b.status = 'COMPLETED' ORDER BY b.completedAt DESC")
    List<Booking> findRecentCompletedBookings(@Param("providerId") Long providerId, Pageable pageable);
}