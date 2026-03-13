package com.aykhedma.dto.request;

import com.aykhedma.model.notification.DevicePlatform;
import lombok.Data;

@Data
public class RegisterDeviceRequest {
    private String deviceId;
    private String fcmToken;
    private DevicePlatform platform;
    private String deviceModel;
    private String appVersion;
    private String osVersion;
}