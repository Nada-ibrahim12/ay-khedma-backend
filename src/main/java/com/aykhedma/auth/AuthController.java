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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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
    @Operation(summary = "User login", description = "Authenticate user and return JWT token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful, JWT token returned", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data or validation error"),
            @ApiResponse(responseCode = "401", description = "Invalid email or password"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {

        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "User registration (JSON)", description = "Register new user with basic information (Admin only)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Registration successful, OTP sent to email"),
            @ApiResponse(responseCode = "400", description = "Validation error - missing or invalid fields"),
            @ApiResponse(responseCode = "409", description = "Conflict - email or phone already registered")
    })
    public ResponseEntity<String> register(
            @Valid @RequestBody RegisterRequest request) {

        authService.register(request, null, null, null, null);

        otpService.generateOtp(request.getEmail());
        return ResponseEntity.ok("Registered successfully. OTP sent to your email.");
    }

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "User registration (Multipart)", description = "Register new consumer or provider with profile picture and documents")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Registration successful, OTP sent to email"),
            @ApiResponse(responseCode = "400", description = "Validation error - invalid fields or missing required files"),
            @ApiResponse(responseCode = "409", description = "Conflict - email or phone already registered"),
            @ApiResponse(responseCode = "413", description = "File too large - exceeds size limit"),
            @ApiResponse(responseCode = "415", description = "Unsupported media type - invalid file format")
    })
    public ResponseEntity<String> registerWithNationalIdImages(
            @Valid @ModelAttribute RegisterRequest request,
            @RequestParam(value = "profilePicture", required = true) MultipartFile profilePicture,
            @RequestParam(value = "nationalIdFrontImage", required = false) MultipartFile nationalIdFrontImage,
            @RequestParam(value = "nationalIdBackImage", required = false) MultipartFile nationalIdBackImage,
            @RequestParam(value = "documents", required = false) List<MultipartFile> documents) {

        authService.register(request, profilePicture, nationalIdFrontImage, nationalIdBackImage, documents);
        otpService.generateOtp(request.getEmail());
        return ResponseEntity.ok("Registered successfully. OTP sent to your email.");
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Verify OTP", description = "Verify email with one-time password")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OTP verified successfully, account activated"),
            @ApiResponse(responseCode = "400", description = "Invalid or expired OTP"),
            @ApiResponse(responseCode = "404", description = "Email not found")
    })
    public ResponseEntity<String> verifyOtp(
            @RequestParam("email") String email,
            @RequestParam("otp") String otp) {

        boolean valid = otpService.validateOtp(email, otp);

        if (!valid) {
            throw new BadRequestException("Invalid OTP");
        }

        authService.verifyOtp(email, otp);

        return ResponseEntity.ok("Account verified");
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Get a new access token using refresh token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "New token generated successfully", content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid or malformed refresh token"),
            @ApiResponse(responseCode = "401", description = "Refresh token expired or revoked")
    })
    public ResponseEntity<AuthResponse> refresh(
            @RequestParam("refreshToken") String refreshToken) {

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
    @Operation(summary = "User logout", description = "Invalidate all refresh tokens for the authenticated user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Logged out successfully"),
            @ApiResponse(responseCode = "401", description = "User not authenticated")
    })
    public ResponseEntity<String> logout(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        refreshTokenRepository.deleteByUser(userDetails.getUser());

        return ResponseEntity.ok("Logged out");
    }

    @PostMapping("/send-otp")
    @Operation(summary = "Send OTP", description = "Send one-time password to user email for verification")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "OTP sent successfully to email"),
            @ApiResponse(responseCode = "400", description = "Invalid email format"),
            @ApiResponse(responseCode = "404", description = "User with this email not found")
    })
    public ResponseEntity<String> sendOtp(@RequestParam("email") String email) {

        userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        otpService.generateOtp(email);

        return ResponseEntity.ok("OTP sent to your email");
    }

}
