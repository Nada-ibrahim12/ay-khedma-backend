package com.aykhedma.dto.response;

import com.aykhedma.model.notification.NotificationPreference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferenceDTO {
    private Long id;
    private Long userId;
    private boolean inAppEnabled;
    private boolean emailEnabled;
    private boolean pushEnabled;
    private LocalDateTime updatedAt;

    public static NotificationPreferenceDTO fromEntity(NotificationPreference preference) {
        return NotificationPreferenceDTO.builder()
                .id(preference.getId())
                .userId(preference.getUserId())
                .inAppEnabled(preference.isInAppEnabled())
                .emailEnabled(preference.isEmailEnabled())
                .pushEnabled(preference.isPushEnabled())
                .updatedAt(preference.getUpdatedAt())
                .build();
    }
}
