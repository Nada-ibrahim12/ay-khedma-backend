package com.aykhedma.controller;


import com.aykhedma.dto.request.RegisterDeviceRequest;
import com.aykhedma.model.notification.DeviceToken;
import com.aykhedma.repository.DeviceTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications/devices")
@RequiredArgsConstructor
public class DeviceTokenController {

    private final DeviceTokenRepository deviceTokenRepository;

    @PostMapping("/register")
    public ResponseEntity<?> registerDevice(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody RegisterDeviceRequest request) {

        // Check if device already exists
        DeviceToken deviceToken = deviceTokenRepository
                .findByUserIdAndDeviceId(userId, request.getDeviceId())
                .orElse(DeviceToken.builder()
                        .userId(userId)
                        .deviceId(request.getDeviceId())
                        .build());

        // Update token
        deviceToken.setFcmToken(request.getFcmToken());
        deviceToken.setPlatform(request.getPlatform());
        deviceToken.setActive(true);
        deviceToken.setLastUsedAt(LocalDateTime.now());

        deviceTokenRepository.save(deviceToken);

        return ResponseEntity.ok(Map.of(
                "message", "Device registered successfully",
                "deviceId", request.getDeviceId()
        ));
    }

    @PostMapping("/unregister")
    public ResponseEntity<?> unregisterDevice(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody Map<String, String> request) {

        String deviceId = request.get("deviceId");

        int deactivated = deviceTokenRepository.deactivateDevice(userId, deviceId);

        if (deactivated > 0) {
            return ResponseEntity.ok(Map.of("message", "Device unregistered successfully"));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/my-devices")
    public ResponseEntity<List<DeviceToken>> getMyDevices(
            @RequestHeader("X-User-Id") Long userId) {

        List<DeviceToken> devices = deviceTokenRepository.findByUserIdAndActiveTrue(userId);
        return ResponseEntity.ok(devices);
    }
}