package com.aykhedma.repository;

import com.aykhedma.model.notification.DeviceToken;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    List<DeviceToken> findByUserIdAndActiveTrue(Long userId);

    Optional<DeviceToken> findByUserIdAndDeviceId(Long userId, String deviceId);

    @Modifying
    @Transactional
    @Query("UPDATE DeviceToken d SET d.active = false WHERE d.userId = :userId AND d.deviceId = :deviceId")
    int deactivateDevice(@Param("userId") Long userId, @Param("deviceId") String deviceId);
}