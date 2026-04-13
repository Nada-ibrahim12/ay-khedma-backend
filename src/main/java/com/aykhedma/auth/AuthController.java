package com.aykhedma.auth;

import com.aykhedma.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import com.aykhedma.dto.request.LoginRequest;
import com.aykhedma.dto.request.RegisterRequest;
import com.aykhedma.dto.response.AuthResponse;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.model.user.RefreshToken;
import com.aykhedma.model.user.User;
import com.aykhedma.repository.RefreshTokenRepository;
import com.aykhedma.repository.UserRepository;
import com.aykhedma.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @Value("${jwt.expiration:3600000}")
    private long jwtExpirationMs;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {

        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> register(
            @Valid @RequestBody RegisterRequest request) {

        authService.register(request);
        otpService.generateOtp(request.getEmail());
        return ResponseEntity.ok("Registered successfully. OTP sent to your email.");
    }

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> registerWithNationalIdImages(
            @Valid @ModelAttribute RegisterRequest request,
            @RequestParam(value = "nationalIdFrontImage", required = false) MultipartFile nationalIdFrontImage,
            @RequestParam(value = "nationalIdBackImage", required = false) MultipartFile nationalIdBackImage) {

        authService.register(request, nationalIdFrontImage, nationalIdBackImage);
        otpService.generateOtp(request.getEmail());
        return ResponseEntity.ok("Registered successfully. OTP sent to your email.");
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<String> verifyOtp(
            @RequestParam("email") String email,
            @RequestParam("otp") String otp) {

        boolean valid = otpService.validateOtp(email, otp);

        if (!valid) {
            throw new BadRequestException("Invalid OTP");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

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
                        .expiresIn(jwtExpirationMs / 1000)
                        .build());
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        refreshTokenRepository.deleteByUser(userDetails.getUser());

        return ResponseEntity.ok("Logged out");
    }

    @PostMapping("/send-otp")
    public ResponseEntity<String> sendOtp(@RequestParam String email) {

        userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        otpService.generateOtp(email);

        return ResponseEntity.ok("OTP sent to your email");
    }

}
