package com.aykhedma.auth;

import com.aykhedma.dto.request.LoginRequest;
import com.aykhedma.dto.request.RegisterRequest;
import com.aykhedma.dto.response.AuthResponse;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.exception.UnauthorizedException;
import com.aykhedma.model.booking.Schedule;
import com.aykhedma.model.location.Location;
import com.aykhedma.model.service.PriceType;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.user.RefreshToken;
import com.aykhedma.model.user.*;
import com.aykhedma.repository.ProviderRepository;
import com.aykhedma.repository.ServiceTypeRepository;
import com.aykhedma.repository.UserRepository;
import com.aykhedma.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Value("${jwt.expiration:3600000}")
    private long jwtExpirationMs;

    public AuthResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmailOrPhone())
                .or(() -> userRepository.findByPhoneNumber(request.getEmailOrPhone()))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        // 🔹 Generate access token
        String jwt = jwtService.generateToken(user);

        // 🔹 Generate refresh token (stored in DB)
        RefreshToken refresh = refreshTokenService.createRefreshToken(user);

        return AuthResponse.builder()
                .token(jwt)
                .refreshToken(refresh.getToken())
                .tokenType("Bearer")
                .expiresIn(jwtExpirationMs / 1000)
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

        } else if (request.getUserType() == UserType.PROVIDER) {

            if (providerRepository.existsByNationalId(request.getNationalId())) {
                throw new BadRequestException("National ID already exists");
            }

            ServiceType serviceType = serviceTypeRepository.findById(request.getServiceTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Service type not found with id: " + request.getServiceTypeId()));

            PriceType priceType;
            try {
                priceType = PriceType.valueOf(request.getPriceType().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid price type: " + request.getPriceType());
            }

            Location location = Location.builder()
                    .latitude(request.getLatitude())
                    .longitude(request.getLongitude())
                    .address(request.getAddress())
                    .area(request.getArea())
                    .city(request.getCity())
                    .build();

            Schedule schedule = Schedule.builder().build();

            Provider provider = Provider.builder()
                    .name(request.getName())
                    .email(request.getEmail())
                    .phoneNumber(request.getPhoneNumber())
                    .password(encodedPassword)
                    .role(UserType.PROVIDER)
                    .enabled(false) // until OTP verified
                    .credentialsNonExpired(true)
                    .verificationStatus(VerificationStatus.PENDING)
                    .bio(request.getBio())
                    .serviceType(serviceType)
                    .location(location)
                    .nationalId(request.getNationalId())
                    .price(request.getPrice())
                    .priceType(priceType)
                    .serviceArea(request.getServiceArea())
                    .serviceAreaRadius(request.getServiceAreaRadius())
                    .schedule(schedule)
                    .build();

            userRepository.save(provider);
        }
    }

}
