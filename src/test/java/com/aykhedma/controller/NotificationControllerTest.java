package com.aykhedma.controller;

import com.aykhedma.dto.request.NotificationPreferenceRequest;
import com.aykhedma.dto.request.NotificationRequest;
import com.aykhedma.dto.response.NotificationDTO;
import com.aykhedma.dto.response.NotificationPreferenceDTO;
import com.aykhedma.model.notification.NotificationType;
import com.aykhedma.security.CustomUserDetails;
import com.aykhedma.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Notification Controller Unit Tests")
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationController notificationController;

    private ObjectMapper objectMapper = new ObjectMapper();

    private final Long USER_ID = 1L;
    private final Long NOTIFICATION_ID = 100L;
    private final String BASE_URL = "/api/v1/notifications";

    private NotificationDTO createTestNotificationDTO() {
        NotificationDTO dto = new NotificationDTO();
        dto.setId(NOTIFICATION_ID);
        dto.setType(NotificationType.BOOKING_CONFIRMATION);
        dto.setTitle("Test Notification");
        dto.setContent("This is a test notification");
        dto.setRead(false);
        dto.setCreatedAt(LocalDateTime.now());
        return dto;
    }

    private CustomUserDetails createMockUserDetails(Long userId) {
        CustomUserDetails userDetails = mock(CustomUserDetails.class);
        com.aykhedma.model.user.User user = mock(com.aykhedma.model.user.User.class);
        when(user.getId()).thenReturn(userId);
        when(userDetails.getUser()).thenReturn(user);
        return userDetails;
    }

    @Nested
    @DisplayName("Get Unread Count Tests")
    class GetUnreadCountTests {
        @Test
        @DisplayName("Should get unread notification count - Success")
        void shouldGetUnreadCount() {
            when(notificationService.getUnreadCount(USER_ID)).thenReturn(5L);

            CustomUserDetails userDetails = createMockUserDetails(USER_ID);
            ResponseEntity<Map<String, Long>> response = notificationController.getUnreadCount(userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsEntry("count", 5L);
            assertThat(response.getBody()).containsEntry("userId", USER_ID);
        }

        @Test
        @DisplayName("Should return 401 when userId is missing")
        void shouldReturnBadRequestWithoutUserId() {
            ResponseEntity<Map<String, Long>> response = notificationController.getUnreadCount(null);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("Get Single Notification Tests")
    class GetSingleNotificationTests {
        @Test
        @DisplayName("Should get notification by ID")
        void shouldGetNotificationById() {
            NotificationDTO dto = createTestNotificationDTO();
            when(notificationService.getNotificationById(USER_ID, NOTIFICATION_ID)).thenReturn(dto);

            CustomUserDetails userDetails = createMockUserDetails(USER_ID);
            ResponseEntity<NotificationDTO> response = notificationController.getNotification(userDetails,
                    NOTIFICATION_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getId()).isEqualTo(NOTIFICATION_ID);
        }

        @Test
        @DisplayName("Should return 404 when notification not found")
        void shouldReturn404WhenNotFound() {
            when(notificationService.getNotificationById(USER_ID, NOTIFICATION_ID))
                    .thenThrow(new RuntimeException("Notification not found"));

            CustomUserDetails userDetails = createMockUserDetails(USER_ID);
            ResponseEntity<NotificationDTO> response = notificationController.getNotification(userDetails,
                    NOTIFICATION_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("Should return 401 when unauthenticated")
        void shouldReturn401WhenUnauthenticated() {
            ResponseEntity<NotificationDTO> response = notificationController.getNotification(null, NOTIFICATION_ID);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("Mark As Read Tests")
    class MarkAsReadTests {
        @Test
        @DisplayName("Should mark notification as read")
        void shouldMarkAsRead() {
            doNothing().when(notificationService).markAsRead(USER_ID, NOTIFICATION_ID);

            CustomUserDetails userDetails = createMockUserDetails(USER_ID);
            ResponseEntity<Map<String, Object>> response = notificationController.markAsRead(userDetails,
                    NOTIFICATION_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsEntry("success", true);
            assertThat(response.getBody()).containsEntry("notificationId", NOTIFICATION_ID);
        }

        @Test
        @DisplayName("Should mark all notifications as read")
        void shouldMarkAllAsRead() {
            doNothing().when(notificationService).markAllAsRead(USER_ID);

            CustomUserDetails userDetails = createMockUserDetails(USER_ID);
            ResponseEntity<Map<String, Object>> response = notificationController.markAllAsRead(userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsEntry("success", true);
        }

        @Test
        @DisplayName("Should mark multiple notifications as read")
        void shouldMarkMultipleAsRead() {
            Map<String, Object> request = new HashMap<>();
            request.put("notificationIds", Arrays.asList(1, 2, 3));

            doNothing().when(notificationService).markAsRead(anyLong(), anyLong());

            CustomUserDetails userDetails = createMockUserDetails(USER_ID);
            ResponseEntity<Map<String, Object>> response = notificationController.markMultipleAsRead(userDetails,
                    request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsEntry("success", true);
            assertThat(response.getBody()).containsEntry("markedCount", 3);
        }
    }

    @Nested
    @DisplayName("Delete Notification Tests")
    class DeleteNotificationTests {
        @Test
        @DisplayName("Should delete notification")
        void shouldDeleteNotification() {
            doNothing().when(notificationService).deleteNotification(USER_ID, NOTIFICATION_ID);

            CustomUserDetails userDetails = createMockUserDetails(USER_ID);
            ResponseEntity<Map<String, Object>> response = notificationController.deleteNotification(userDetails,
                    NOTIFICATION_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsEntry("success", true);
        }

        @Test
        @DisplayName("Should clear all notifications")
        void shouldClearAll() {
            when(notificationService.deleteAllNotifications(USER_ID)).thenReturn(5);

            CustomUserDetails userDetails = createMockUserDetails(USER_ID);
            ResponseEntity<?> response = notificationController.clearAllNotifications(userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertThat(body).containsEntry("success", true);
            assertThat(body).containsEntry("deletedCount", 5);
        }
    }

    @Nested
    @DisplayName("Preferences Tests")
    class PreferencesTests {
        @Test
        @DisplayName("Should get notification preferences")
        void shouldGetPreferences() {
            NotificationPreferenceDTO dto = new NotificationPreferenceDTO();
            dto.setInAppEnabled(true);
            dto.setEmailEnabled(false);
            dto.setPushEnabled(true);

            when(notificationService.getUserPreferences(USER_ID)).thenReturn(dto);

            CustomUserDetails userDetails = createMockUserDetails(USER_ID);
            ResponseEntity<NotificationPreferenceDTO> response = notificationController.getPreferences(userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().isInAppEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should update notification preferences")
        void shouldUpdatePreferences() {
            NotificationPreferenceRequest request = new NotificationPreferenceRequest();
            request.setInAppEnabled(true);
            request.setEmailEnabled(false);
            request.setPushEnabled(true);

            NotificationPreferenceDTO dto = new NotificationPreferenceDTO();
            dto.setInAppEnabled(true);
            dto.setEmailEnabled(false);
            dto.setPushEnabled(true);

            when(notificationService.updateUserPreferences(eq(USER_ID), any())).thenReturn(dto);

            CustomUserDetails userDetails = createMockUserDetails(USER_ID);
            ResponseEntity<NotificationPreferenceDTO> response = notificationController.updatePreferences(userDetails,
                    request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("Get By Type Tests")
    class GetByTypeTests {
        @Test
        @DisplayName("Should get notifications by type")
        void shouldGetByType() {
            when(notificationService.getNotificationsByType(eq(USER_ID), any(NotificationType.class)))
                    .thenReturn(Arrays.asList(createTestNotificationDTO()));

            CustomUserDetails userDetails = createMockUserDetails(USER_ID);
            ResponseEntity<List<NotificationDTO>> response = notificationController.getByType(
                    userDetails, NotificationType.BOOKING_CONFIRMATION, PageRequest.of(0, 10));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Get User Notifications Tests")
    class GetUserNotificationsTests {
        @Test
        @DisplayName("Should get notifications with authentication")
        void shouldGetNotificationsWithHeader() {
            Page<NotificationDTO> page = new PageImpl<>(
                    Arrays.asList(createTestNotificationDTO()),
                    PageRequest.of(0, 20),
                    1);

            when(notificationService.getUserNotifications(eq(USER_ID), any(Pageable.class))).thenReturn(page);

            CustomUserDetails userDetails = createMockUserDetails(USER_ID);
            ResponseEntity<Page<NotificationDTO>> response = notificationController.getUserNotifications(
                    userDetails, PageRequest.of(0, 20));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getContent()).hasSize(1);
        }

        @Test
        @DisplayName("Should return 401 when userId is missing")
        void shouldReturnBadRequestWithoutUserId() {
            ResponseEntity<Page<NotificationDTO>> response = notificationController.getUserNotifications(
                    null, PageRequest.of(0, 20));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("Failed Notifications Tests")
    class FailedNotificationsTests {
        @Test
        @DisplayName("Should get failed notifications")
        void shouldGetFailedNotifications() {
            when(notificationService.listFailedNotifications(USER_ID))
                    .thenReturn(Arrays.asList(createTestNotificationDTO()));

            CustomUserDetails userDetails = createMockUserDetails(USER_ID);
            ResponseEntity<List<NotificationDTO>> response = notificationController.getFailedNotifications(userDetails);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Filter Notifications Tests")
    class FilterNotificationsTests {
        @Test
        @DisplayName("Should filter by date range")
        void shouldFilterByDateRange() {
            Page<NotificationDTO> page = new PageImpl<>(
                    Arrays.asList(createTestNotificationDTO()),
                    PageRequest.of(0, 20),
                    1);

            when(notificationService.getUserNotificationsByDateRange(
                    eq(USER_ID), any(), any(), any(Pageable.class))).thenReturn(page);

            CustomUserDetails userDetails = createMockUserDetails(USER_ID);
            ResponseEntity<Page<NotificationDTO>> response = notificationController.filterNotifications(
                    userDetails,
                    LocalDateTime.of(2024, 1, 1, 0, 0),
                    LocalDateTime.of(2024, 12, 31, 23, 59),
                    PageRequest.of(0, 20));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Send Email Notification Tests")
    class SendEmailNotificationTests {
        @Test
        @DisplayName("Should send email notification")
        void shouldSendEmailNotification() {
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("userId", USER_ID);
            requestData.put("type", "BOOKING_CONFIRMATION");
            requestData.put("title", "Test Email");
            requestData.put("content", "Test content");

            NotificationRequest request = NotificationRequest.builder()
                    .userId(USER_ID)
                    .type(NotificationType.BOOKING_CONFIRMATION)
                    .title("Test Email")
                    .content("Test content")
                    .build();

            doNothing().when(notificationService).sendNotification(any(NotificationRequest.class));

            ResponseEntity<Map<String, Object>> response = notificationController.sendEmailNotification(
                    "test@test.com", request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsEntry("success", true);
        }
    }

    @Nested
    @DisplayName("Send Test Notification Tests")
    class SendTestNotificationTests {
        @Test
        @DisplayName("Should send test notification")
        void shouldSendTestNotification() {
            NotificationRequest request = NotificationRequest.builder()
                    .userId(USER_ID)
                    .type(NotificationType.GENERAL)
                    .title("Test Notification")
                    .content("This is a test")
                    .deepLink("/test")
                    .build();

            doNothing().when(notificationService).sendNotification(any(NotificationRequest.class));

            CustomUserDetails userDetails = createMockUserDetails(USER_ID);
            ResponseEntity<Map<String, Object>> response = notificationController.sendInAppNotification(userDetails,
                    request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).containsEntry("success", true);
        }

        @Test
        @DisplayName("Should return 401 when user is not authenticated")
        void shouldReturn401WhenUnauthenticated() {
            NotificationRequest request = NotificationRequest.builder().build();
            ResponseEntity<Map<String, Object>> response = notificationController.sendInAppNotification(null, request);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}