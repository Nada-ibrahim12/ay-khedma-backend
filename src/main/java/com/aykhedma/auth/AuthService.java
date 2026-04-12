package com.aykhedma.auth;

import com.aykhedma.dto.request.LoginRequest;
import com.aykhedma.dto.request.RegisterRequest;
import com.aykhedma.dto.response.AuthResponse;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.exception.UnauthorizedException;
import com.aykhedma.model.document.Document;
import com.aykhedma.model.booking.Schedule;
import com.aykhedma.model.location.Location;
import com.aykhedma.model.service.PriceType;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.user.*;
import com.aykhedma.repository.DocumentRepository;
import com.aykhedma.repository.ProviderRepository;
import com.aykhedma.repository.ServiceTypeRepository;
import com.aykhedma.repository.UserRepository;
import com.aykhedma.service.FileStorageService;
import com.aykhedma.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;
    private final DocumentRepository documentRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final FileStorageService fileStorageService;

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
        register(request, null, null);
    }

    public void register(RegisterRequest request,
            MultipartFile nationalIdFrontImage,
            MultipartFile nationalIdBackImage) {

        if (userRepository.existsByEmail(request.getEmail()))
            throw new RuntimeException("Email already exists");

        if (userRepository.existsByPhoneNumber(request.getPhoneNumber()))
            throw new RuntimeException("Phone already exists");

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        if (request.getUserType() == UserType.CONSUMER) {

            Location location = Location.builder()
                    .latitude(request.getLatitude())
                    .longitude(request.getLongitude())
                    .address(request.getAddress())
                    .area(request.getArea())
                    .city(request.getCity())
                    .build();

            Consumer consumer = Consumer.builder()
                    .name(request.getName())
                    .email(request.getEmail())
                    .phoneNumber(request.getPhoneNumber())
                    .password(encodedPassword)
                    .role(UserType.CONSUMER)
                    .enabled(false) // until OTP verified
                    .credentialsNonExpired(true)
                    .location(location)
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

            String frontImageUrl = null;
            String backImageUrl = null;

            try {
                if (nationalIdFrontImage != null && !nationalIdFrontImage.isEmpty()) {
                    frontImageUrl = fileStorageService.storeFile(nationalIdFrontImage, "national-id-images");
                }

                if (nationalIdBackImage != null && !nationalIdBackImage.isEmpty()) {
                    backImageUrl = fileStorageService.storeFile(nationalIdBackImage, "national-id-images");
                }

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
                        .yearsOfExperience(request.getYearsOfExperience())
                        .serviceType(serviceType)
                        .location(location)
                        .nationalId(request.getNationalId())
                        .nationalIdFrontImage(frontImageUrl)
                        .nationalIdBackImage(backImageUrl)
                        .price(request.getPrice())
                        .priceType(priceType)
                        .schedule(schedule)
                        .build();

                Provider savedProvider = (Provider) userRepository.save(provider);
                saveNationalIdDocument(savedProvider, nationalIdFrontImage, frontImageUrl, "NATIONAL_ID", "National ID Front");
                saveNationalIdDocument(savedProvider, nationalIdBackImage, backImageUrl, "NATIONAL_ID", "National ID Back");
            } catch (IOException e) {
                if (frontImageUrl != null) {
                    fileStorageService.deleteFile(frontImageUrl);
                }
                if (backImageUrl != null) {
                    fileStorageService.deleteFile(backImageUrl);
                }
                throw new BadRequestException("Failed to upload national ID images");
            } catch (RuntimeException e) {
                if (frontImageUrl != null) {
                    fileStorageService.deleteFile(frontImageUrl);
                }
                if (backImageUrl != null) {
                    fileStorageService.deleteFile(backImageUrl);
                }
                throw e;
            }
        }
    }

    private void saveNationalIdDocument(Provider provider,
            MultipartFile sourceFile,
            String fileUrl,
            String documentType,
            String title) {
        if (sourceFile == null || sourceFile.isEmpty() || fileUrl == null) {
            return;
        }

        String documentTitle = sourceFile.getOriginalFilename();
        if (documentTitle == null || documentTitle.isBlank()) {
            documentTitle = title;
        }

        Document document = Document.builder()
                .title(documentTitle)
                .type(documentType)
                .filePath(fileUrl)
                .provider(provider)
                .build();

        documentRepository.save(document);
    }

}
