package com.aykhedma.repository;

import com.aykhedma.model.notification.Notification;
import com.aykhedma.model.notification.NotificationType;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // This returns a Page (supports pagination)
    Page<Notification> findByUserId(Long userId, Pageable pageable);

    // This returns a List (no pagination)
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("SELECT n FROM Notification n WHERE n.userId = :userId ORDER BY n.createdAt DESC")
    Page<Notification> findUserNotificationsSorted(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.userId = :userId AND n.isRead = false ORDER BY n.sentAt DESC")
    List<Notification> findUnreadByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.userId = :userId AND n.isRead = false")
    long countUnreadByUserId(@Param("userId") Long userId);

    List<Notification> findByUserIdAndTypeOrderBySentAtDesc(Long userId, NotificationType type);

    // FIXED: Removed unused readAt parameter
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP WHERE n.userId = :userId AND n.isRead = false")
    int markAllAsRead(@Param("userId") Long userId);

    // Alternative if you need custom timestamp:
    /*
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.userId = :userId AND n.isRead = false")
    int markAllAsRead(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);
    */

    @Query("SELECT n FROM Notification n WHERE n.userId = :userId ORDER BY n.sentAt DESC")
    List<Notification> findRecentNotifications(@Param("userId") Long userId, Pageable pageable);

    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.createdAt < :expiryDate")
    int deleteOldNotifications(@Param("expiryDate") LocalDateTime expiryDate);

    // Optional: Add method to find by status
    List<Notification> findByStatus(String status);

    // Optional: Find unread count only
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.userId = :userId AND n.isRead = false")
    long getUnreadCount(@Param("userId") Long userId);
}