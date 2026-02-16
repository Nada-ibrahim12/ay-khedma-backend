package com.aykhedma.dto.response;

import com.aykhedma.model.user.UserType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private Long id;
    private String name;
    private String email;
    private String phoneNumber;
    private UserType role;
    private String profileImage;
    private String preferredLanguage;
    private LocalDateTime createdAt;
    private boolean enabled;
}