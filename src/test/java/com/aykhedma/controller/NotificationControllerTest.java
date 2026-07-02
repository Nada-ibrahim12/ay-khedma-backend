package com.aykhedma.controller;

import com.aykhedma.config.TestSecurityConfig;
import com.aykhedma.dto.response.NotificationDTO;
import com.aykhedma.dto.request.NotificationPreferenceRequest;
import com.aykhedma.exception.GlobalExceptionHandler;
import com.aykhedma.model.notification.NotificationType;
import com.aykhedma.service.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ TestSecurityConfig.class, GlobalExceptionHandler.class })
@DisplayName("Notification Controller Integration Tests")
class NotificationControllerTest {

    private static final class PrincipalUser {
        private final Long id;

        private PrincipalUser(Long id) {
            this.id = id;
        }

        public Long getId() {
            return id;
        }
    }

    private static final class PrincipalPayload {
        private final PrincipalUser user;

        private PrincipalPayload(Long userId) {
            this.user = new PrincipalUser(userId);
        }

        public PrincipalUser getUser() {
            return user;
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NotificationService notificationService;

    private final Long USER_ID = 1L;
    private final Long NOTIFICATION_ID = 100L;
    private final String BASE_URL = "/api/v1/notifications";

    private RequestPostProcessor authenticatedUser() {
        var principal = new PrincipalPayload(USER_ID);
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_CONSUMER"));
        var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        return SecurityMockMvcRequestPostProcessors.authentication(auth);
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

    @Test
    @DisplayName("Should get unread notification count")
    void shouldGetUnreadCount() throws Exception {
        when(notificationService.getUnreadCount(USER_ID)).thenReturn(5L);

        mockMvc.perform(get(BASE_URL + "/unread/count")
                .with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5));
    }

    @Test
    @DisplayName("Should get notification by ID")
    void shouldGetNotificationById() throws Exception {
        when(notificationService.getNotificationById(USER_ID, NOTIFICATION_ID))
                .thenReturn(createTestNotificationDTO());

        mockMvc.perform(get(BASE_URL + "/{notificationId}", NOTIFICATION_ID)
                .with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(NOTIFICATION_ID));
    }

    @Test
    @DisplayName("Should return 404 when notification not found")
    void shouldReturn404WhenNotFound() throws Exception {
        when(notificationService.getNotificationById(USER_ID, NOTIFICATION_ID))
                .thenThrow(new RuntimeException("Notification not found"));

        mockMvc.perform(get(BASE_URL + "/{notificationId}", NOTIFICATION_ID)
                .with(authenticatedUser()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should mark notification as read")
    void shouldMarkAsRead() throws Exception {
        doNothing().when(notificationService).markAsRead(USER_ID, NOTIFICATION_ID);

        mockMvc.perform(put(BASE_URL + "/{notificationId}/read", NOTIFICATION_ID)
                .with(authenticatedUser())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Should mark all notifications as read")
    void shouldMarkAllAsRead() throws Exception {
        doNothing().when(notificationService).markAllAsRead(USER_ID);

        mockMvc.perform(put(BASE_URL + "/read-all")
                .with(authenticatedUser()))
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
                .with(authenticatedUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Should delete notification")
    void shouldDeleteNotification() throws Exception {
        doNothing().when(notificationService).deleteNotification(USER_ID, NOTIFICATION_ID);

        mockMvc.perform(delete(BASE_URL + "/{notificationId}", NOTIFICATION_ID)
                .with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Should get notification preferences")
    void shouldGetPreferences() throws Exception {
        mockMvc.perform(get(BASE_URL + "/preferences")
                .with(authenticatedUser()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should update notification preferences")
    void shouldUpdatePreferences() throws Exception {
        NotificationPreferenceRequest request = new NotificationPreferenceRequest();
        request.setInAppEnabled(true);
        request.setEmailEnabled(false);
        request.setPushEnabled(true);

        mockMvc.perform(put(BASE_URL + "/preferences")
                .with(authenticatedUser())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should get notifications by type")
    void shouldGetByType() throws Exception {
        when(notificationService.getNotificationsByType(eq(USER_ID), any()))
                .thenReturn(Arrays.asList(createTestNotificationDTO()));

        mockMvc.perform(get(BASE_URL + "/types/{type}", NotificationType.BOOKING_CONFIRMATION)
                .with(authenticatedUser()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should clear all notifications")
    void shouldClearAll() throws Exception {
        when(notificationService.deleteAllNotifications(USER_ID)).thenReturn(5);

        mockMvc.perform(delete(BASE_URL + "/clear/all")
                .with(authenticatedUser()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("Should get failed notifications")
    void shouldGetFailedNotifications() throws Exception {
        when(notificationService.listFailedNotifications(USER_ID))
                .thenReturn(Arrays.asList(createTestNotificationDTO()));

        mockMvc.perform(get(BASE_URL + "/failed")
                .with(authenticatedUser()))
                .andExpect(status().isOk());
    }
}