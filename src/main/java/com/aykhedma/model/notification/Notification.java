package com.aykhedma.model.notification;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.*;
import java.util.HashMap;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "User ID is required")
    @Column(nullable = false)
    private Long userId;

    @NotBlank(message = "Title is required")
    @Size(min = 2, max = 100, message = "Title must be between 2 and 100 characters")
    @Column(nullable = false, length = 100)
    private String title;

    @Size(max = 1000, message = "Body cannot exceed 1000 characters")
    @Column(length = 1000)
    private String body;

    @NotNull(message = "Notification type is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationType type;

    @ElementCollection
    @CollectionTable(name = "notification_methods", joinColumns = @JoinColumn(name = "notification_id"))
    @Column(name = "method")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<NotificationChannel> methods = new HashSet<>();

    @Column(name = "push_sent")
    private boolean pushSent = false;

    @Column(name = "push_sent_at")
    private LocalDateTime pushSentAt;

    @Column(name = "push_failed")
    private boolean pushFailed = false;

    @Column(name = "email_sent")
    private boolean emailSent = false;

    @Column(name = "email_sent_at")
    private LocalDateTime emailSentAt;

    @Column(name = "email_failed")
    private boolean emailFailed = false;

    @Column(name = "in_app_delivered")
    private boolean inAppDelivered = false;

    @Column(name = "in_app_delivered_at")
    private LocalDateTime inAppDeliveredAt;

    @Column(name = "in_app_failed")
    private boolean inAppFailed = false;

    @ElementCollection
    @CollectionTable(name = "notification_data", joinColumns = @JoinColumn(name = "notification_id"))
    @MapKeyColumn(name = "data_key")
    @Column(name = "data_value", length = 500)
    @Builder.Default
    private Map<@NotBlank String, @Size(max = 500) String> data = new HashMap<>();

    @PastOrPresent(message = "Sent date cannot be in the future")
    @CreationTimestamp
    private LocalDateTime sentAt;

    @PastOrPresent(message = "Read date cannot be in the future")
    private LocalDateTime readAt;
    @Column(name = "is_delivered")
    private boolean delivered = false;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Size(max = 500, message = "Image URL cannot exceed 500 characters")
    @Pattern(regexp = "^(http|https|ftp)://.*$", message = "Invalid image URL format")
    private String imageUrl;

    @Size(max = 500, message = "Deep link cannot exceed 500 characters")
    private String deepLink;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = NotificationStatus.PENDING;
        }
    }
}