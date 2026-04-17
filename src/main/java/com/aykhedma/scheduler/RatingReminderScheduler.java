package com.aykhedma.scheduler;

import com.aykhedma.dto.request.NotificationRequest;
import com.aykhedma.model.booking.Booking;
import com.aykhedma.model.notification.NotificationType;
import com.aykhedma.repository.BookingRepository;
import com.aykhedma.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * Scheduler for sending rating reminders to users.
 * Runs daily at midnight to remind users of pending reviews for services that have started.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RatingReminderScheduler {

    private final BookingRepository bookingRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 0 * * *") // Runs at midnight every day
    @Transactional
    public void sendRatingReminders() {
        log.info("Running daily rating reminders...");
        
        // Find bookings that are ACCEPTED and have started at least 30 minutes ago
        // but are missing one or both ratings.
        LocalDate today = LocalDate.now();
        LocalTime nowMinus30m = LocalTime.now().minusMinutes(30);

        List<Booking> bookings = bookingRepository.findBookingsNeedingRating(today, nowMinus30m);

        for (Booking booking : bookings) {
            // Remind Consumer if they haven't rated the provider
            if (booking.getConsumerRating() == null) {
                sendConsumerReminder(booking);
            }
            // Remind Provider if they haven't rated the consumer
            if (booking.getProviderRating() == null) {
                sendProviderReminder(booking);
            }
        }
        log.info("Completed daily rating reminders for {} bookings.", bookings.size());
    }

    private void sendConsumerReminder(Booking booking) {
        NotificationRequest request = NotificationRequest.builder()
                .userId(booking.getConsumer().getId())
                .type(NotificationType.RATING_REMINDER)
                .title("Rate Your Service")
                .content("Don't forget to rate your service for " + booking.getServiceType().getName() + " by " + booking.getProvider().getName())
                .sendInApp(true)
                .sendPush(true)
                .data(Map.of("bookingId", booking.getId().toString(), "type", "CONSUMER_RATING"))
                .build();
        notificationService.sendNotification(request);
    }

    private void sendProviderReminder(Booking booking) {
        NotificationRequest request = NotificationRequest.builder()
                .userId(booking.getProvider().getId())
                .type(NotificationType.RATING_REMINDER)
                .title("Rate Your Consumer")
                .content("Don't forget to rate your consumer " + booking.getConsumer().getName() + " for the service " + booking.getServiceType().getName())
                .sendInApp(true)
                .sendPush(true)
                .data(Map.of("bookingId", booking.getId().toString(), "type", "PROVIDER_RATING"))
                .build();
        notificationService.sendNotification(request);
    }
}
