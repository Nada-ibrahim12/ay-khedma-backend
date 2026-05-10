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
import com.aykhedma.model.service.RiskLevel;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.user.*;
import com.aykhedma.repository.DocumentRepository;
import com.aykhedma.repository.ProviderRepository;
import com.aykhedma.repository.ServiceTypeRepository;
import com.aykhedma.repository.UserRepository;
import com.aykhedma.service.FileStorageService;
import com.aykhedma.security.JwtService;
import com.aykhedma.service.verification.VerificationService;
import com.aykhedma.dto.response.verification.NidExtractionResponse;
import com.aykhedma.dto.response.verification.FaceMatchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;
import java.util.List;
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
    private final OtpService otpService;
    private final VerificationService verificationService;

    @Value("${jwt.expiration:3600000}")
    private long jwtExpirationMs;

    public AuthResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmailOrPhone())
                .or(() -> userRepository.findByPhoneNumber(request.getEmailOrPhone()))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        if (!user.isEnabled()) {
            throw new UnauthorizedException("Your account is suspended. Please contact support.");
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

    public void verifyOtp(String email, String otp) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setEnabled(true);
        userRepository.save(user);
    }

    @Transactional
    public void register(RegisterRequest request,
            MultipartFile profilePicture,
            MultipartFile nationalIdFrontImage,
            MultipartFile nationalIdBackImage,
            MultipartFile selfie,
            List<MultipartFile> documents) {

        String profileImageUrl = null;

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

            Consumer savedConsumer = (Consumer) userRepository.save(consumer);

            try {
                profileImageUrl = storeProfilePicture(profilePicture, savedConsumer.getId().toString());
                savedConsumer.setProfileImage(profileImageUrl);
                userRepository.save(savedConsumer);
            } catch (IOException e) {
                throw new BadRequestException("Failed to upload profile picture");
            }

        } else if (request.getUserType() == UserType.ADMIN) {

            Admin admin = Admin.builder()
                    .name(request.getName())
                    .email(request.getEmail())
                    .phoneNumber(request.getPhoneNumber())
                    .password(encodedPassword)
                    .role(UserType.ADMIN)
                    .enabled(true) // Admins enabled by default for testing, or set to false if OTP is required
                    .credentialsNonExpired(true)
                    .build();

            userRepository.save(admin);

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
            String selfieImageUrl = null;

            try {
                validateProviderDocuments(serviceType, nationalIdFrontImage, nationalIdBackImage, selfie, documents);

                // Build provider first to generate ID
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
                        .price(request.getPrice())
                        .priceType(priceType)
                        .schedule(schedule)
                        .build();

                Provider savedProvider = (Provider) userRepository.save(provider);
                String folderName = savedProvider.getId().toString();

                profileImageUrl = storeProfilePicture(profilePicture, folderName);
                savedProvider.setProfileImage(profileImageUrl);

                if (nationalIdFrontImage != null && !nationalIdFrontImage.isEmpty()) {
                    frontImageUrl = fileStorageService.storeFile(nationalIdFrontImage, folderName);
                }

                if (nationalIdBackImage != null && !nationalIdBackImage.isEmpty()) {
                    backImageUrl = fileStorageService.storeFile(nationalIdBackImage, folderName);
                }

                if (selfie != null && !selfie.isEmpty()) {
                    selfieImageUrl = fileStorageService.storeFile(selfie, folderName);
                }

                savedProvider.setNationalIdFrontImage(frontImageUrl);
                savedProvider.setNationalIdBackImage(backImageUrl);
                savedProvider.setSelfieImage(selfieImageUrl);
                savedProvider = (Provider) userRepository.save(savedProvider);

                saveNationalIdDocument(savedProvider, nationalIdFrontImage, frontImageUrl, "NATIONAL_ID",
                        "National ID Front");
                saveNationalIdDocument(savedProvider, nationalIdBackImage, backImageUrl, "NATIONAL_ID",
                        "National ID Back");

                // 🛡️ Automatic Verification using OCR & Face Matching (STRICT)
                if (nationalIdFrontImage != null && !nationalIdFrontImage.isEmpty() && selfie != null && !selfie.isEmpty()) {
                    String verificationError = verifyProviderStrictly(savedProvider, request.getNationalId(), nationalIdFrontImage, selfie);
                    if (verificationError != null) {
                        // This will trigger rollback because of @Transactional
                        throw new BadRequestException(verificationError);
                    }
                }

                if (documents != null && !documents.isEmpty()) {
                    for (MultipartFile file : documents) {
                        if (file == null || file.isEmpty())
                            continue;

                        String fileUrl = fileStorageService.storeFile(file, folderName);

                        Document doc = Document.builder()
                                .title(file.getOriginalFilename())
                                .type("CERTIFICATE") // later make dynamic
                                .filePath(fileUrl)
                                .provider(savedProvider)
                                .build();

                        documentRepository.save(doc);
                    }
                }
            } catch (IOException e) {
                if (profileImageUrl != null) {
                    fileStorageService.deleteFile(profileImageUrl);
                }
                if (frontImageUrl != null) {
                    fileStorageService.deleteFile(frontImageUrl);
                }
                if (backImageUrl != null) {
                    fileStorageService.deleteFile(backImageUrl);
                }
                if (selfieImageUrl != null) {
                    fileStorageService.deleteFile(selfieImageUrl);
                }
                throw new BadRequestException("Failed to upload national ID images");
            } catch (RuntimeException e) {
                if (profileImageUrl != null) {
                    fileStorageService.deleteFile(profileImageUrl);
                }
                if (frontImageUrl != null) {
                    fileStorageService.deleteFile(frontImageUrl);
                }
                if (backImageUrl != null) {
                    fileStorageService.deleteFile(backImageUrl);
                }
                if (selfieImageUrl != null) {
                    fileStorageService.deleteFile(selfieImageUrl);
                }
                throw e;
            }
        }
    }

    private void validateProviderDocuments(ServiceType serviceType,
            MultipartFile front,
            MultipartFile back,
            MultipartFile selfie,
            List<MultipartFile> documents) {

        if (front == null || front.isEmpty()) {
            throw new BadRequestException("National ID front image is required");
        }

        if (back == null || back.isEmpty()) {
            throw new BadRequestException("National ID back image is required");
        }

        if (selfie == null || selfie.isEmpty()) {
            throw new BadRequestException("Selfie image is required for verification");
        }

        if (serviceType.getRiskLevel() == RiskLevel.HIGH) {
            boolean hasValidDocument = documents != null
                    && documents.stream().anyMatch(doc -> doc != null && !doc.isEmpty());
            if (!hasValidDocument) {
                throw new BadRequestException("You must upload additional documents for HIGH risk services");
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

    private String storeProfilePicture(MultipartFile profilePicture, String folderName) throws IOException {
        if (profilePicture == null || profilePicture.isEmpty()) {
            return null;
        }

        return fileStorageService.storeFile(profilePicture, folderName);
    }

    public void forgotPassword(String email) {
        if (!userRepository.existsByEmail(email)) {
            throw new ResourceNotFoundException("User not found with email: " + email);
        }

        otpService.generatePasswordResetOtp(email);
    }

    @Transactional
    public void resetPassword(String email, String otp, String newPassword) {
        if (!userRepository.existsByEmail(email)) {
            throw new ResourceNotFoundException("User not found with email: " + email);
        }

        boolean isValid = otpService.validateOtp(email, otp);
        if (!isValid) {
            throw new BadRequestException("Invalid or expired OTP");
        }

        userRepository.updatePassword(email, passwordEncoder.encode(newPassword));
    }

    private String verifyProviderStrictly(Provider provider, String expectedNid, MultipartFile idImage, MultipartFile selfie) {
        try {
            // 1. Extract NID and compare
            NidExtractionResponse nidResult = verificationService.extractNid(idImage);
            boolean nidMatched = nidResult.isValid() && expectedNid.equals(nidResult.getNid());
            provider.setNidVerified(nidMatched);

            // 2. Match faces
            FaceMatchResponse faceResult = verificationService.matchFaces(idImage, selfie);
            provider.setFaceMatched(faceResult.isMatch());
            provider.setFaceMatchConfidence(faceResult.getDistance());

            // 3. Set status and return specific error if failed
            if (nidMatched && faceResult.isMatch()) {
                provider.setVerificationStatus(VerificationStatus.VERIFIED);
                providerRepository.save(provider);
                return null; // Success
            } else {
                StringBuilder reason = new StringBuilder();
                if (!nidMatched) reason.append("National ID mismatch: The ID on the card does not match the number provided. ");
                if (!faceResult.isMatch()) reason.append("Face verification failed: The selfie does not match the photo on the ID card. ");
                
                String errorMsg = reason.toString().trim();
                provider.setRejectionReason(errorMsg);
                providerRepository.save(provider);
                return errorMsg;
            }
        } catch (Exception e) {
            // If the service is DOWN, we allow the registration to continue as PENDING 
            // so an admin can verify manually later.
            return null; 
        }
    }
}
