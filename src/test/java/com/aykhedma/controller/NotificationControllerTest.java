package com.aykhedma.controller;

import com.aykhedma.config.TestSecurityConfig;
import com.aykhedma.dto.response.NotificationDTO;
import com.aykhedma.dto.request.NotificationRequest;
import com.aykhedma.exception.GlobalExceptionHandler;
import com.aykhedma.model.notification.NotificationType;
import com.aykhedma.model.notification.NotificationStatus;
import com.aykhedma.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("Notification Controller Unit Tests")
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NotificationService notificationService;

    private final Long USER_ID = 1L;
    private final Long NOTIFICATION_ID = 100L;
    private final String BASE_URL = "/api/v1/notifications";

    @BeforeEach
    void setUp() {
        // ObjectMapper is autowired
    }

    @Nested
    @DisplayName("Get User Notifications Tests")
    class GetUserNotificationsTests {

        @Test
        @DisplayName("Should get notifications for user with header userId")
        void shouldGetNotificationsWithHeader() throws Exception {
            when(notificationService.getUserNotifications(USER_ID, PageRequest.of(0, 20)))
                    .thenReturn(new PageImpl<>(Arrays.asList(createTestNotificationDTO())));

            mockMvc.perform(get(BASE_URL)
                    .header("X-User-Id", USER_ID)
                    .param("page", "0")
                    .param("size", "20"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should get notifications for user with param userId")
        void shouldGetNotificationsWithParam() throws Exception {
            when(notificationService.getUserNotifications(USER_ID, PageRequest.of(0, 20)))
                    .thenReturn(new PageImpl<>(Arrays.asList(createTestNotificationDTO())));

            mockMvc.perform(get(BASE_URL)
                    .param("userId", USER_ID.toString())
                    .param("page", "0")
                    .param("size", "20"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Should return bad request when userId is missing")
        void shouldReturnBadRequestWithoutUserId() throws Exception {
            mockMvc.perform(get(BASE_URL)
                    .param("page", "0")
                    .param("size", "20"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Get Unread Count Tests")
    class GetUnreadCountTests {

        @Test
        @DisplayName("Should get unread notification count")
        void shouldGetUnreadCount() throws Exception {
            when(notificationService.getUnreadCount(USER_ID)).thenReturn(5L);

            mockMvc.perform(get(BASE_URL + "/unread/count")
                    .header("X-User-Id", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count").value(5));
        }

        @Test
        @DisplayName("Should return bad request when userId is missing")
        void shouldReturnBadRequestWithoutUserId() throws Exception {
            mockMvc.perform(get(BASE_URL + "/unread/count"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Get Single Notification Tests")
    class GetSingleNotificationTests {

        @Test
        @DisplayName("Should get notification by ID")
        void shouldGetNotificationById() throws Exception {
            when(notificationService.getNotificationById(USER_ID, NOTIFICATION_ID))
                    .thenReturn(createTestNotificationDTO());

            mockMvc.perform(get(BASE_URL + "/" + NOTIFICATION_ID)
                    .header("X-User-Id", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(NOTIFICATION_ID));
        }

        @Test
        @DisplayName("Should return 404 when notification not found")
        void shouldReturn404WhenNotFound() throws Exception {
            when(notificationService.getNotificationById(USER_ID, NOTIFICATION_ID))
                    .thenThrow(new RuntimeException("Notification not found"));

            mockMvc.perform(get(BASE_URL + "/" + NOTIFICATION_ID)
                    .header("X-User-Id", USER_ID))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Mark As Read Tests")
    class MarkAsReadTests {

        @Test
        @DisplayName("Should mark single notification as read")
        void shouldMarkAsRead() throws Exception {
            doNothing().when(notificationService).markAsRead(USER_ID, NOTIFICATION_ID);

            mockMvc.perform(put(BASE_URL + "/" + NOTIFICATION_ID + "/read")
                    .header("X-User-Id", USER_ID)
                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("Should mark multiple notifications as read")
        void shouldMarkMultipleAsRead() throws Exception {
            Map<String, Object> request = new HashMap<>();
            request.put("notificationIds", Arrays.asList(1, 2, 3));

            doNothing().when(notificationService).markAsRead(anyLong(), anyLong());

            mockMvc.perform(put(BASE_URL + "/read-batch")
                    .header("X-User-Id", USER_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("Should mark all notifications as read")
        void shouldMarkAllAsRead() throws Exception {
            doNothing().when(notificationService).markAllAsRead(USER_ID);

            mockMvc.perform(put(BASE_URL + "/read-all")
                    .header("X-User-Id", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("Delete Notification Tests")
    class DeleteNotificationTests {

        @Test
        @DisplayName("Should delete notification")
        void shouldDeleteNotification() throws Exception {
            doNothing().when(notificationService).deleteNotification(USER_ID, NOTIFICATION_ID);

            mockMvc.perform(delete(BASE_URL + "/" + NOTIFICATION_ID)
                    .header("X-User-Id", USER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("Send Test Notification Tests")
    class SendTestNotificationTests {

        @Test
        @DisplayName("Should send test notification")
        void shouldSendTestNotification() throws Exception {
            NotificationRequest request = NotificationRequest.builder()
                    .userId(USER_ID)
                    .title("Test")
                    .content("This is a test")
                    .type(NotificationType.BOOKING_CONFIRMATION)
                    .sendInApp(true)
                    .sendPush(true)
                    .build();

            doNothing().when(notificationService).sendNotification(any());

            mockMvc.perform(post(BASE_URL + "/test/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("Send Email Notification Tests")
    class SendEmailNotificationTests {

        @Test
        @DisplayName("Should send email notification")
        void shouldSendEmailNotification() throws Exception {
            String email = "test@example.com";
            NotificationRequest request = NotificationRequest.builder()
                    .userId(USER_ID)
                    .title("Email Test")
                    .content("This is an email test")
                    .type(NotificationType.BOOKING_CONFIRMATION)
                    .build();

            doNothing().when(notificationService).sendNotification(any());

            mockMvc.perform(post(BASE_URL + "/send-email")
                    .param("email", email)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.email").value(email));
        }
    }

    @Nested
    @DisplayName("Filter Notifications Tests")
    class FilterNotificationsTests {

        @Test
        @DisplayName("Should filter notifications by date range")
        void shouldFilterByDateRange() throws Exception {
            LocalDateTime startDate = LocalDateTime.now().minusDays(7);
            LocalDateTime endDate = LocalDateTime.now();
            Page<NotificationDTO> page = new PageImpl<>(Arrays.asList(createTestNotificationDTO()));

            when(notificationService.getUserNotificationsByDateRange(
                    eq(USER_ID), any(LocalDateTime.class), any(LocalDateTime.class), any()))
                    .thenReturn(page);

            mockMvc.perform(get(BASE_URL + "/filter")
                    .header("X-User-Id", USER_ID)
                    .param("startDate", startDate.toString())
                    .param("endDate", endDate.toString())
                    .param("page", "0")
                    .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(NOTIFICATION_ID));
        }
    }

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
}
