package com.aykhedma.scheduler;

import com.aykhedma.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Component
@RequiredArgsConstructor
public class BookingExpiryScheduler
{
    private final BookingRepository bookingRepository;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void expireBookings ()
    {
        bookingRepository.expirePendingBookings(LocalDateTime.now(), LocalDate.now(), LocalTime.now());
        bookingRepository.expireAcceptedBookings(LocalDateTime.now(), LocalDate.now().minusDays(1), LocalTime.now());
    }
}
