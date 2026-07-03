package com.aykhedma.service;

import com.aykhedma.dto.request.UpdateUserRequest;
import com.aykhedma.dto.response.AdminProviderResponse;
import com.aykhedma.dto.response.DashboardStatsResponse;
import com.aykhedma.dto.response.ProviderResponse;
import com.aykhedma.dto.response.UserResponse;
import com.aykhedma.exception.ResourceNotFoundException;
import com.aykhedma.mapper.ProviderMapper;
import com.aykhedma.mapper.UserMapper;
import com.aykhedma.model.notification.NotificationType;
import com.aykhedma.model.chat.ChatRoom;
import com.aykhedma.model.user.Provider;
import com.aykhedma.model.user.User;
import com.aykhedma.model.user.UserType;
import com.aykhedma.model.user.VerificationStatus;
import com.aykhedma.repository.BookingRepository;
import com.aykhedma.repository.ChatRoomRepository;
import com.aykhedma.repository.ChatSessionRepository;
import com.aykhedma.repository.ConsumerRepository;
import com.aykhedma.repository.DeviceTokenRepository;
import com.aykhedma.repository.DocumentRepository;
import com.aykhedma.repository.EmergencyRequestRepository;
import com.aykhedma.repository.InteractionRatingRepository;
import com.aykhedma.repository.NotificationPreferenceRepository;
import com.aykhedma.repository.NotificationRepository;
import com.aykhedma.repository.ProviderResponseRepository;
import com.aykhedma.repository.RefreshTokenRepository;
import com.aykhedma.repository.ProviderRepository;
import com.aykhedma.repository.ServiceTypeRepository;
import com.aykhedma.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final ProviderRepository providerRepository;
    private final ProviderMapper providerMapper;
    private final ProviderService providerService;
    private final NotificationFactory notificationFactory;
    private final ConsumerRepository consumerRepository;
    private final BookingRepository bookingRepository;
    private final EmergencyRequestRepository emergencyRequestRepository;
    private final InteractionRatingRepository interactionRatingRepository;
    private final ProviderResponseRepository providerResponseRepository;
    private final DocumentRepository documentRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public DashboardStatsResponse getDashboardStats() {
        return DashboardStatsResponse.builder()
                .totalUsers(userRepository.count())
                .totalProviders(providerRepository.count())
                .pendingProviders(providerRepository.countByVerificationStatus(VerificationStatus.PENDING))
                .totalServices(serviceTypeRepository.countServices())
                .build();
    }

    @Override
    public List<ProviderResponse> getPendingProviders() {
        List<Provider> pendingProviders = providerRepository.findByVerificationStatus(VerificationStatus.PENDING);
        return providerMapper.toProviderResponseList(pendingProviders);
    }

    @Override
    public ProviderResponse getProviderDetails(Long providerId) {
        ProviderResponse response = providerService.getProviderProfile(providerId);
        response.setDocuments(providerService.getProviderDocuments(providerId));
        return response;
    }

    @Override
    @Transactional
    public ProviderResponse approveProvider(Long providerId) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found with id: " + providerId));

        provider.setVerificationStatus(VerificationStatus.VERIFIED);
        provider.setRejectionReason(null);

        Provider savedProvider = providerRepository.save(provider);
        notificationFactory.send(savedProvider.getId(),
                NotificationType.PROVIDER_ACCEPTED,
                Map.of(
                        "title", "AyKhedma - Account Approved!",
                        "status", "accepted",
                        "statusLabel", "Accepted",
                        "message", "Your registration application has been reviewed and approved.",
                        "content",
                        "Congratulations! Your registration application has been reviewed and approved.\n\n" +
                                "Your account is now fully active, and you can start receiving booking requests on the platform.\n\n"
                                +
                                "Welcome to the AyKhedma family!"));

        return providerMapper.toProviderResponse(savedProvider);
    }

    @Override
    @Transactional
    public ProviderResponse rejectProvider(Long providerId, String reason) {
        Provider provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found with id: " + providerId));

        provider.setVerificationStatus(VerificationStatus.REJECTED);
        provider.setRejectionReason(reason);

        Provider savedProvider = providerRepository.save(provider);
        String rejectionReason = (reason == null || reason.isBlank()) ? "No reason provided." : reason;
        notificationFactory.send(savedProvider.getId(),
                NotificationType.PROVIDER_REJECTED,
                Map.of(
                        "title", "AyKhedma - Application Status Update",
                        "status", "rejected",
                        "statusLabel", "Rejected",
                        "reason", rejectionReason,
                        "message", "Your registration application has been rejected.",
                        "content",
                        "We regret to inform you that your registration application has been rejected for the following reason:\n\n"
                                +
                                "\"" + rejectionReason + "\"\n\n" +
                                "Please address these issues and update your profile or contact support for more details."));

        return providerMapper.toProviderResponse(savedProvider);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> searchUsers(
            UserType role, Boolean status,
            LocalDateTime startDate, LocalDateTime endDate,
            String keyword,
            Pageable pageable) {

        // Cap page size to prevent expensive unbounded requests from admin UI
        int maxPageSize = 100;
        Pageable cappedPageable = org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), Math.min(pageable.getPageSize(), maxPageSize), pageable.getSort());

        // Pass role as String for native query
        String roleStr = (role != null) ? role.name() : null;
        Page<Object[]> rows = userRepository.searchUsersNative(roleStr, status, startDate, endDate, keyword, cappedPageable);

        return rows.map(row -> UserResponse.builder()
                .id(((Number) row[0]).longValue())
                .name((String) row[1])
                .email((String) row[2])
                .phoneNumber((String) row[3])
                .role(row[4] != null ? UserType.valueOf((String) row[4]) : null)
                .profileImage((String) row[5])
                .preferredLanguage((String) row[6])
                .createdAt(row[7] != null ? ((java.sql.Timestamp) row[7]).toLocalDateTime() : null)
                .enabled(row[8] != null && (Boolean) row[8])
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminProviderResponse> searchProviders(
            String keyword, VerificationStatus status,
            Boolean enabled, Pageable pageable) {

        // Cap page size to prevent very large admin queries
        int maxPageSize = 100;
        Pageable cappedPageable = org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), Math.min(pageable.getPageSize(), maxPageSize), pageable.getSort());

        Page<Provider> providers = providerRepository.findAllProvidersForAdmin(keyword, status, enabled,
                cappedPageable);

        return providers.map(providerMapper::toAdminProviderResponse);
    }

    @Override
    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName());
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().isBlank()) {
            user.setPhoneNumber(request.getPhoneNumber());
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            user.setEmail(request.getEmail());
        }
        if (request.getEnabled() != null) {
            user.setEnabled(request.getEnabled());
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        userRepository.save(user);
        return userMapper.toUserResponse(user);
    }

    @Override
    @Transactional
    public UserResponse suspendUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        user.setEnabled(false);

        if (user instanceof Provider) {
            notificationFactory.send(userId,
                    NotificationType.ACCOUNT_UPDATE,
                    Map.of(
                            "title", "AyKhedma - Account Status Update",
                            "status", "suspended",
                            "statusLabel", "Suspended",
                            "message", "Your provider account has been suspended due to a violation of platform rules.",
                            "content",
                            "We are writing to inform you that your provider account has been suspended due to a violation of our platform rules.\n\n"
                                    +
                                    "If you believe this is a mistake, please contact our support team."));
        }

        return userMapper.toUserResponse(user);
    }

    @Override
    @Transactional
    public UserResponse reactivateUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        user.setEnabled(true);
        return userMapper.toUserResponse(user);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        notificationPreferenceRepository.deleteByUserId(userId);
        notificationRepository.deleteByUserId(userId);
        deviceTokenRepository.deleteByUserId(userId);
        refreshTokenRepository.deleteByUser(user);
        chatSessionRepository.deleteByUserId(userId);

        List<ChatRoom> chatRooms = chatRoomRepository.findAllByParticipant(user);
        if (!chatRooms.isEmpty()) {
            chatRoomRepository.deleteAll(chatRooms);
        }

        userRepository.deleteSavedProviderLinks(userId);

        if (user instanceof Provider) {
            bookingRepository.deleteByProviderId(userId);
            emergencyRequestRepository.deleteBySelectedProviderId(userId);
            interactionRatingRepository.deleteByProviderId(userId);
            providerResponseRepository.deleteByProviderId(userId);
            documentRepository.deleteByProviderId(userId);
        } else if (user instanceof com.aykhedma.model.user.Consumer) {
            bookingRepository.deleteByConsumerId(userId);
            emergencyRequestRepository.deleteByConsumerId(userId);
            interactionRatingRepository.deleteByConsumerId(userId);
        }

        userRepository.delete(user);
    }
}
