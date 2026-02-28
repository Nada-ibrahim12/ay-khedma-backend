package com.aykhedma.service;

import com.aykhedma.dto.response.NotificationDTO;
import com.aykhedma.dto.request.NotificationRequest;
import com.aykhedma.model.notification.Notification;
import com.aykhedma.model.notification.NotificationType;
import com.aykhedma.model.notification.NotificationStatus;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.util.TestDataFactory;
import com.aykhedma.repository.NotificationRepository;
import com.aykhedma.repository.UserRepository;
import com.aykhedma.service.notification.EmailService;
import com.aykhedma.service.notification.FirebaseService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Notification Service Unit Tests")
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FirebaseService firebaseService;

    @Mock
    private EmailService emailService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private NotificationService notificationService;

    @Captor
    private ArgumentCaptor<Notification> notificationCaptor;

    private Notification notification;
    private NotificationRequest notificationRequest;
    private Consumer user;
    private final Long USER_ID = 1L;
    private final Long NOTIFICATION_ID = 100L;
    private final Long NON_EXISTENT_ID = 999L;

    @BeforeEach
    void setUp() {
        user = createTestUser();
        notification = createTestNotification();
        notificationRequest = createTestNotificationRequest();
    }

    @Nested
    @DisplayName("Send Notification Tests")
    class SendNotificationTests {

        @Test
        @DisplayName("Should send notification to all channels when all flags are true")
        void shouldSendNotificationToAllChannels() {
            notificationRequest.setSendInApp(true);
            notificationRequest.setSendPush(true);
            notificationRequest.setSendEmail(true);
            
            when(notificationRepository.save(any(Notification.class)))
                    .thenReturn(notification);
            when(userRepository.findById(USER_ID))
                    .thenReturn(Optional.of(user));
            when(firebaseService.isFirebaseEnabled()).thenReturn(true);

            notificationService.sendNotification(notificationRequest);

            verify(notificationRepository, atLeastOnce()).save(any(Notification.class));
            verify(messagingTemplate, times(2)).convertAndSendToUser(anyString(), anyString(), any());
            verify(firebaseService).sendPushNotification(USER_ID, 
                    notificationRequest.getTitle(),
                    notificationRequest.getContent(),
                    notificationRequest.getData());
            verify(emailService).sendHtmlEmail(anyString(), anyString(), anyString(), anyMap());
        }

        @Test
        @DisplayName("Should only send WebSocket notification when sendInApp is true")
        void shouldOnlySendWebSocketNotification() {
            notificationRequest.setSendInApp(true);
            notificationRequest.setSendPush(false);
            notificationRequest.setSendEmail(false);
            
            when(notificationRepository.save(any(Notification.class)))
                    .thenReturn(notification);
            when(notificationRepository.countUnreadByUserId(USER_ID)).thenReturn(1L);

            notificationService.sendNotification(notificationRequest);

            verify(messagingTemplate, times(2)).convertAndSendToUser(anyString(), anyString(), any());
            verify(firebaseService, never()).sendPushNotification(anyLong(), anyString(), anyString(), anyMap());
            verify(emailService, never()).sendHtmlEmail(anyString(), anyString(), anyString(), anyMap());
        }

        @Test
        @DisplayName("Should not send notification if save fails")
        void shouldNotSendIfSaveFails() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(notificationRepository.save(any(Notification.class)))
                    .thenThrow(new RuntimeException("Database error"));

            notificationService.sendNotification(notificationRequest);

            verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
            verify(firebaseService, never()).sendPushNotification(anyLong(), anyString(), anyString(), anyMap());
        }
    }

    @Nested
    @DisplayName("Get Notifications Tests")
    class GetNotificationsTests {

        @Test
        @DisplayName("Should retrieve all notifications for user")
        void shouldGetUserNotifications() {
            List<Notification> notifications = Arrays.asList(notification);
            Page<Notification> page = new PageImpl<>(notifications);
            Pageable pageable = PageRequest.of(0, 20);

            when(notificationRepository.findByUserId(USER_ID, pageable))
                    .thenReturn(page);

            Page<NotificationDTO> result = notificationService.getUserNotifications(USER_ID, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(1);
            verify(notificationRepository).findByUserId(USER_ID, pageable);
        }

        @Test
        @DisplayName("Should return empty page when no notifications exist")
        void shouldReturnEmptyPage() {
            Page<Notification> emptyPage = new PageImpl<>(Arrays.asList());
            Pageable pageable = PageRequest.of(0, 20);

            when(notificationRepository.findByUserId(USER_ID, pageable))
                    .thenReturn(emptyPage);

            Page<NotificationDTO> result = notificationService.getUserNotifications(USER_ID, pageable);

            assertThat(result.getTotalElements()).isEqualTo(0);
            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("Should get unread notification count")
        void shouldGetUnreadCount() {
            when(notificationRepository.countUnreadByUserId(USER_ID)).thenReturn(5L);

            long count = notificationService.getUnreadCount(USER_ID);

            assertThat(count).isEqualTo(5L);
            verify(notificationRepository).countUnreadByUserId(USER_ID);
        }

        @Test
        @DisplayName("Should get notification by ID")
        void shouldGetNotificationById() {
            when(notificationRepository.findById(NOTIFICATION_ID))
                    .thenReturn(Optional.of(notification));

            NotificationDTO result = notificationService.getNotificationById(USER_ID, NOTIFICATION_ID);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(NOTIFICATION_ID);
        }

        @Test
        @DisplayName("Should throw exception for non-existent notification")
        void shouldThrowExceptionForNonExistentNotification() {
            when(notificationRepository.findById(NON_EXISTENT_ID))
                    .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> notificationService.getNotificationById(USER_ID, NON_EXISTENT_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("not found");
        }

        @Test
        @DisplayName("Should throw exception when user is unauthorized")
        void shouldThrowExceptionForUnauthorizedUser() {
            notification.setUserId(2L); // Different user
            when(notificationRepository.findById(NOTIFICATION_ID))
                    .thenReturn(Optional.of(notification));

            // Act & Assert
            assertThatThrownBy(() -> notificationService.getNotificationById(USER_ID, NOTIFICATION_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Unauthorized");
        }
    }

    @Nested
    @DisplayName("Mark As Read Tests")
    class MarkAsReadTests {

        @Test
        @DisplayName("Should mark notification as read")
        void shouldMarkAsRead() {
            when(notificationRepository.findById(NOTIFICATION_ID))
                    .thenReturn(Optional.of(notification));
            when(notificationRepository.countUnreadByUserId(USER_ID)).thenReturn(0L);

            notificationService.markAsRead(USER_ID, NOTIFICATION_ID);

            verify(notificationRepository).save(notificationCaptor.capture());
            Notification saved = notificationCaptor.getValue();
            assertThat(saved.isRead()).isTrue();
            assertThat(saved.getReadAt()).isNotNull();
        }

        @Test
        @DisplayName("Should mark all notifications as read")
        void shouldMarkAllAsRead() {
            when(notificationRepository.markAllAsRead(USER_ID)).thenReturn(5);

            notificationService.markAllAsRead(USER_ID);

            verify(notificationRepository).markAllAsRead(USER_ID);
            verify(messagingTemplate).convertAndSendToUser(
                    eq(USER_ID.toString()),
                    eq("/queue/notifications/count"),
                    anyMap()
            );
        }
    }

    @Nested
    @DisplayName("Delete Notification Tests")
    class DeleteNotificationTests {

        @Test
        @DisplayName("Should delete notification")
        void shouldDeleteNotification() {
            when(notificationRepository.findById(NOTIFICATION_ID))
                    .thenReturn(Optional.of(notification));

            notificationService.deleteNotification(USER_ID, NOTIFICATION_ID);

            verify(notificationRepository).deleteById(NOTIFICATION_ID);
        }

        @Test
        @DisplayName("Should throw exception when deleting unauthorized notification")
        void shouldThrowExceptionWhenUnauthorized() {
            notification.setUserId(2L); // Different user
            when(notificationRepository.findById(NOTIFICATION_ID))
                    .thenReturn(Optional.of(notification));

            // Act & Assert
            assertThatThrownBy(() -> notificationService.deleteNotification(USER_ID, NOTIFICATION_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Unauthorized");
        }
    }

    @Nested
    @DisplayName("Cleanup Notifications Tests")
    class CleanupNotificationsTests {

        @Test
        @DisplayName("Should cleanup old notifications")
        void shouldCleanupOldNotifications() {
            when(notificationRepository.deleteOldNotifications(any(LocalDateTime.class)))
                    .thenReturn(10);

            notificationService.cleanupOldNotifications(30);

            verify(notificationRepository).deleteOldNotifications(any(LocalDateTime.class));
        }
    }

    @Nested
    @DisplayName("Filter Notifications by Date Range Tests")
    class FilterNotificationsByDateRangeTests {

        @Test
        @DisplayName("Should filter notifications by date range")
        void shouldFilterByDateRange() {
            List<Notification> notifications = Arrays.asList(notification);
            LocalDateTime startDate = LocalDateTime.now().minusDays(7);
            LocalDateTime endDate = LocalDateTime.now();
            Pageable pageable = PageRequest.of(0, 20);

            when(notificationRepository.findByUserIdAndCreatedAtBetween(
                    USER_ID, startDate, endDate, pageable))
                    .thenReturn(notifications);

            Page<NotificationDTO> result = notificationService.getUserNotificationsByDateRange(
                    USER_ID, startDate, endDate, pageable);

            assertThat(result).isNotNull();
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should return empty list when no notifications in date range")
        void shouldReturnEmptyWhenNoNotificationsInRange() {
            LocalDateTime startDate = LocalDateTime.now().minusDays(7);
            LocalDateTime endDate = LocalDateTime.now();
            Pageable pageable = PageRequest.of(0, 20);

            when(notificationRepository.findByUserIdAndCreatedAtBetween(
                    USER_ID, startDate, endDate, pageable))
                    .thenReturn(Arrays.asList());

            Page<NotificationDTO> result = notificationService.getUserNotificationsByDateRange(
                    USER_ID, startDate, endDate, pageable);

            assertThat(result.getTotalElements()).isEqualTo(0);
        }
    }

    // Test data factory methods
    private Notification createTestNotification() {
        return Notification.builder()
                .id(NOTIFICATION_ID)
                .userId(USER_ID)
                .title("Test Notification")
                .body("This is a test notification")
                .type(NotificationType.BOOKING_CONFIRMATION)
                .status(NotificationStatus.PENDING)
                .isRead(false)
                .delivered(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private NotificationRequest createTestNotificationRequest() {
        return NotificationRequest.builder()
                .userId(USER_ID)
                .title("Test Notification")
                .content("This is a test notification")
                .type(NotificationType.BOOKING_CONFIRMATION)
                .sendInApp(true)
                .sendPush(true)
                .sendEmail(true)
                .build();
    }

    private Consumer createTestUser() {
        return TestDataFactory.createConsumer(USER_ID);
    }
}
