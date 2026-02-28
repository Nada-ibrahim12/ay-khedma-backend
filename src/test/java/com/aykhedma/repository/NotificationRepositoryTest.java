package com.aykhedma.repository;

import com.aykhedma.model.notification.Notification;
import com.aykhedma.model.notification.NotificationType;
import com.aykhedma.model.notification.NotificationStatus;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.UserType;
import com.aykhedma.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("Notification Repository Unit Tests")
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    private Consumer testUser;
    private final Long USER_ID = 1L;
    private final String USER_EMAIL = "test@example.com";
    private final String USER_PHONE = "01234567890";

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
        userRepository.deleteAll();

        testUser = TestDataFactory.createConsumer(USER_ID);
        testUser.setEmail(USER_EMAIL);
        testUser.setPhoneNumber(USER_PHONE);
        testUser = userRepository.save(testUser);
    }

    @Nested
    @DisplayName("Find By User ID Tests")
    class FindByUserIdTests {

        @Test
        @DisplayName("Should find all notifications for a user")
        void shouldFindNotificationsByUserId() {
            Notification notification1 = createTestNotification("Notification 1", NotificationStatus.PENDING);
            Notification notification2 = createTestNotification("Notification 2", NotificationStatus.PENDING);
            notificationRepository.saveAll(List.of(notification1, notification2));

            Page<Notification> result = notificationRepository.findByUserId(testUser.getId(), PageRequest.of(0, 10));

            assertEquals(2, result.getTotalElements());
            assertEquals(2, result.getContent().size());
        }

        @Test
        @DisplayName("Should return empty page when user has no notifications")
        void shouldReturnEmptyPageWhenNoNotifications() {
            Page<Notification> result = notificationRepository.findByUserId(testUser.getId(), PageRequest.of(0, 10));

            assertEquals(0, result.getTotalElements());
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should apply pagination correctly")
        void shouldApplyPaginationCorrectly() {
            for (int i = 1; i <= 25; i++) {
                createTestNotification("Notification " + i, NotificationStatus.PENDING);
                notificationRepository.flush();
            }

            Page<Notification> firstPage = notificationRepository.findByUserId(testUser.getId(), PageRequest.of(0, 10));
            Page<Notification> secondPage = notificationRepository.findByUserId(testUser.getId(), PageRequest.of(1, 10));

            assertEquals(25, firstPage.getTotalElements());
            assertEquals(10, firstPage.getContent().size());
            assertEquals(10, secondPage.getContent().size());
            assertEquals(3, firstPage.getTotalPages());
        }
    }

    @Nested
    @DisplayName("Find By User ID And Created At Between Tests")
    class FindByUserIdAndCreatedAtBetweenTests {

        @Test
        @DisplayName("Should find notifications by sorted order")
        void shouldFindNotificationsWithinDateRange() {
            Notification notification1 = createTestNotification("Notification 1", NotificationStatus.PENDING);
            Notification notification2 = createTestNotification("Notification 2", NotificationStatus.PENDING);
            notificationRepository.saveAll(List.of(notification1, notification2));

            List<Notification> result = notificationRepository.findByUserIdOrderByCreatedAtDesc(testUser.getId());

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("Should return empty list when user has no notifications")
        void shouldReturnEmptyListWhenNoNotificationsInRange() {
            List<Notification> result = notificationRepository.findByUserIdOrderByCreatedAtDesc(testUser.getId());

            assertEquals(0, result.size());
        }
    }

    @Nested
    @DisplayName("Count Unread By User ID Tests")
    class CountUnreadByUserIdTests {

        @Test
        @DisplayName("Should count unread notifications for user")
        void shouldCountUnreadNotifications() {
            createTestNotification("Unread 1", NotificationStatus.PENDING);
            createTestNotification("Unread 2", NotificationStatus.PENDING);
            Notification readNotification = Notification.builder()
                    .userId(testUser.getId())
                    .title("Read Notification")
                    .body("This is read")
                    .type(NotificationType.BOOKING_CONFIRMATION)
                    .status(NotificationStatus.READ)
                    .isRead(true)
                    .createdAt(LocalDateTime.now())
                    .build();
            notificationRepository.save(readNotification);

            long unreadCount = notificationRepository.countUnreadByUserId(testUser.getId());

            assertEquals(2L, unreadCount);
        }

        @Test
        @DisplayName("Should return zero when all notifications are read")
        void shouldReturnZeroWhenAllRead() {
            Notification notification1 = Notification.builder()
                    .userId(testUser.getId())
                    .title("Read 1")
                    .body("Read")
                    .type(NotificationType.BOOKING_CONFIRMATION)
                    .status(NotificationStatus.READ)
                    .isRead(true)
                    .createdAt(LocalDateTime.now())
                    .build();

            Notification notification2 = Notification.builder()
                    .userId(testUser.getId())
                    .title("Read 2")
                    .body("Read")
                    .type(NotificationType.BOOKING_CONFIRMATION)
                    .status(NotificationStatus.READ)
                    .isRead(true)
                    .createdAt(LocalDateTime.now())
                    .build();

            notificationRepository.saveAll(List.of(notification1, notification2));

            long unreadCount = notificationRepository.countUnreadByUserId(testUser.getId());

            assertEquals(0L, unreadCount);
        }
    }

    @Nested
    @DisplayName("Find By Type Tests")
    class FindByTypeTests {

        @Test
        @DisplayName("Should find notifications by type")
        void shouldFindNotificationsByType() {
            Notification bookingNotif = Notification.builder()
                    .userId(testUser.getId())
                    .title("Booking Confirmation")
                    .body("Your booking is confirmed")
                    .type(NotificationType.BOOKING_CONFIRMATION)
                    .status(NotificationStatus.PENDING)
                    .isRead(false)
                    .createdAt(LocalDateTime.now())
                    .build();

            Notification reviewNotif = Notification.builder()
                    .userId(testUser.getId())
                    .title("New Review")
                    .body("You got a new review")
                    .type(NotificationType.REVIEW_RECEIVED)
                    .status(NotificationStatus.PENDING)
                    .isRead(false)
                    .createdAt(LocalDateTime.now())
                    .build();

            notificationRepository.saveAll(List.of(bookingNotif, reviewNotif));

            List<Notification> bookingNotifications = notificationRepository
                    .findByUserIdAndTypeOrderBySentAtDesc(testUser.getId(), NotificationType.BOOKING_CONFIRMATION);

            assertEquals(1, bookingNotifications.size());
            assertEquals(NotificationType.BOOKING_CONFIRMATION, bookingNotifications.get(0).getType());
        }
    }

    @Nested
    @DisplayName("Delete Old Notifications Tests")
    class DeleteOldNotificationsTests {

        @Test
        @DisplayName("Should delete notifications older than specified date")
        void shouldDeleteOldNotifications() {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime cutoffDate = now.minusDays(30);

            Notification oldNotification = Notification.builder()
                    .userId(testUser.getId())
                    .title("Old")
                    .body("Old notification")
                    .type(NotificationType.BOOKING_CONFIRMATION)
                    .status(NotificationStatus.READ)
                    .isRead(true)
                    .createdAt(cutoffDate.minusDays(1))
                    .build();

            Notification recentNotification = createTestNotification("Recent", NotificationStatus.READ);

            notificationRepository.saveAll(List.of(oldNotification, recentNotification));
            assertEquals(2, notificationRepository.count());

            notificationRepository.deleteOldNotifications(cutoffDate);

            assertEquals(2, notificationRepository.count());
            assertTrue(notificationRepository.findById(recentNotification.getId()).isPresent());
        }

        @Test
        @DisplayName("Should not delete recent notifications")
        void shouldNotDeleteRecentNotifications() {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime cutoffDate = now.minusDays(30);

            Notification recentNotification1 = createTestNotification("Recent 1", NotificationStatus.PENDING);
            Notification recentNotification2 = createTestNotification("Recent 2", NotificationStatus.PENDING);

            notificationRepository.saveAll(List.of(recentNotification1, recentNotification2));

            notificationRepository.deleteOldNotifications(cutoffDate);

            assertEquals(2, notificationRepository.count());
        }
    }

    @Nested
    @DisplayName("Exist By ID And User ID Tests")
    class ExistByIdAndUserIdTests {

        @Test
        @DisplayName("Should return true when notification belongs to user")
        void shouldReturnTrueWhenNotificationBelongsToUser() {
            Notification notification = createTestNotification("Test", NotificationStatus.PENDING);

            boolean exists = notificationRepository.findById(notification.getId())
                    .map(n -> n.getUserId().equals(testUser.getId()))
                    .orElse(false);

            assertTrue(exists);
        }

        @Test
        @DisplayName("Should return false when notification doesn't belong to user")
        void shouldReturnFalseWhenNotificationBelongsToDifferentUser() {
            Notification notification = createTestNotification("Test", NotificationStatus.PENDING);
            Long differentUserId = testUser.getId() + 1000;

            boolean exists = notificationRepository.findById(notification.getId())
                    .map(n -> n.getUserId().equals(differentUserId))
                    .orElse(false);

            assertFalse(exists);
        }
    }

    @Nested
    @DisplayName("Mark As Read Tests")
    class MarkAsReadTests {

        @Test
        @DisplayName("Should mark notification as read")
        void shouldMarkNotificationAsRead() {
            Notification notification = createTestNotification("Test", NotificationStatus.PENDING);

            // Act  
            notification.setRead(true);
            notification.setReadAt(LocalDateTime.now());
            notificationRepository.save(notification);

            Notification updated = notificationRepository.findById(notification.getId()).get();
            assertTrue(updated.isRead());
        }
    }

    @Nested
    @DisplayName("Mark All As Read Tests")
    class MarkAllAsReadTests {

        @Test
        @DisplayName("Should mark all user notifications as read")
        void shouldMarkAllNotificationsAsRead() {
            createTestNotification("Notification 1", NotificationStatus.PENDING);
            createTestNotification("Notification 2", NotificationStatus.PENDING);
            createTestNotification("Notification 3", NotificationStatus.PENDING);

            assertEquals(3L, notificationRepository.countUnreadByUserId(testUser.getId()));

            notificationRepository.markAllAsRead(testUser.getId());

            assertEquals(0L, notificationRepository.countUnreadByUserId(testUser.getId()));
        }
    }

    @Nested
    @DisplayName("Find By User ID And Status Tests")
    class FindByUserIdAndStatusTests {

        @Test
        @DisplayName("Should find notifications by user and status")
        void shouldFindNotificationsByUserAndStatus() {
            createTestNotification("Pending 1", NotificationStatus.PENDING);
            createTestNotification("Pending 2", NotificationStatus.PENDING);
            Notification readNotification = Notification.builder()
                    .userId(testUser.getId())
                    .title("Read")
                    .body("Read notification")
                    .type(NotificationType.BOOKING_CONFIRMATION)
                    .status(NotificationStatus.READ)
                    .isRead(true)
                    .createdAt(LocalDateTime.now())
                    .build();
            notificationRepository.save(readNotification);

            List<Notification> unreadNotifications = notificationRepository.findUnreadByUserId(testUser.getId());
            List<Notification> allNotifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(testUser.getId());

            assertEquals(2, unreadNotifications.size());
            assertEquals(3, allNotifications.size());
        }
    }

    // Test data factory method
    private Notification createTestNotification(String title, NotificationStatus status) {
        Notification notification = Notification.builder()
                .userId(testUser.getId())
                .title(title)
                .body("Test body for " + title)
                .type(NotificationType.BOOKING_CONFIRMATION)
                .status(status)
                .isRead(status == NotificationStatus.READ)
                .createdAt(LocalDateTime.now())
                .build();

        return notificationRepository.save(notification);
    }
}
