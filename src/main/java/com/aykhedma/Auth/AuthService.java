package com.aykhedma.Auth;

import com.aykhedma.dto.request.LoginRequest;
import com.aykhedma.dto.request.RegisterRequest;
import com.aykhedma.dto.response.AuthResponse;
import com.aykhedma.model.RefreshToken;
import com.aykhedma.model.user.Consumer;
import com.aykhedma.model.user.User;
import com.aykhedma.model.user.UserType;
import com.aykhedma.repository.UserRepository;
import com.aykhedma.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService; // ✅ ADD THIS

    public AuthResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmailOrPhone())
                .or(() -> userRepository.findByPhoneNumber(request.getEmailOrPhone()))
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }

        // 🔹 Generate access token
        String jwt = jwtService.generateToken(user);

        // 🔹 Generate refresh token (stored in DB)
        RefreshToken refresh =
                refreshTokenService.createRefreshToken(user);

        return AuthResponse.builder()
                .token(jwt)
                .refreshToken(refresh.getToken())
                .tokenType("Bearer")
                .expiresIn(3600L)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .verified(user.isEnabled())
                .build();
    }

    public void register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail()))
            throw new RuntimeException("Email already exists");

        if (userRepository.existsByPhoneNumber(request.getPhoneNumber()))
            throw new RuntimeException("Phone already exists");

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        if (request.getUserType() == UserType.CONSUMER) {

            Consumer consumer = Consumer.builder()
                    .name(request.getName())
                    .email(request.getEmail())
                    .phoneNumber(request.getPhoneNumber())
                    .password(encodedPassword)
                    .role(UserType.CONSUMER)
                    .enabled(false) // until OTP verified
                    .credentialsNonExpired(true)
                    .totalBookings(0)
                    .build();

            userRepository.save(consumer);

        } else {
            throw new RuntimeException("Provider registration requires extra data");
        }
    }

}
