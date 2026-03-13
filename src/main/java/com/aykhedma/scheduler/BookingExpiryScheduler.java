package com.aykhedma.scheduler;

import com.aykhedma.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;

@Component
@RequiredArgsConstructor
public class BookingExpiryScheduler
{
    private final BookingRepository bookingRepository;

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void expirePendingBookings ()
    {
        bookingRepository.expirePendingBookings(LocalDate.now(), LocalTime.now());
    }
}
