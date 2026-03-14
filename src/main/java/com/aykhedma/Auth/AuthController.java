package com.aykhedma.Auth;
import com.aykhedma.security.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.aykhedma.dto.request.LoginRequest;
import com.aykhedma.dto.request.RegisterRequest;
import com.aykhedma.dto.response.AuthResponse;
import com.aykhedma.model.RefreshToken;
import com.aykhedma.model.user.User;
import com.aykhedma.repository.RefreshTokenRepository;
import com.aykhedma.repository.UserRepository;
import com.aykhedma.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OtpService otpService;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {

        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ResponseEntity<String> register(
            @Valid @RequestBody RegisterRequest request) {

        authService.register(request);
        return ResponseEntity.ok("Registered successfully. Verify OTP.");
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<String> verifyOtp(
            @RequestParam String phone,
            @RequestParam String otp) {

        boolean valid = otpService.validateOtp(phone, otp);

        if (!valid) {
            throw new RuntimeException("Invalid OTP");
        }

        User user = userRepository.findByPhoneNumber(phone)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setEnabled(true);
        userRepository.save(user);

        return ResponseEntity.ok("Account verified");
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @RequestParam String refreshToken) {

        RefreshToken token = refreshTokenService.verify(refreshToken);

        String newJwt = jwtService.generateToken(token.getUser());

        return ResponseEntity.ok(
                AuthResponse.builder()
                        .token(newJwt)
                        .refreshToken(refreshToken)
                        .tokenType("Bearer")
                        .expiresIn(3600L)
                        .build()
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        refreshTokenRepository.deleteByUser(userDetails.getUser());

        return ResponseEntity.ok("Logged out");
    }
    @PostMapping("/send-otp")
    public ResponseEntity<String> sendOtp(@RequestParam String phone) {

        otpService.generateOtp(phone);

        return ResponseEntity.ok("OTP sent");
    }

}
