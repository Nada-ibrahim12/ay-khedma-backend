package com.aykhedma.service;

import com.aykhedma.model.chat.ChatRoom;
import com.aykhedma.model.service.PriceType;
import com.aykhedma.model.service.RiskLevel;
import com.aykhedma.model.service.ServiceType;
import com.aykhedma.model.user.Consumer;
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
import com.aykhedma.repository.ProviderRepository;
import com.aykhedma.repository.ProviderResponseRepository;
import com.aykhedma.repository.RefreshTokenRepository;
import com.aykhedma.repository.ServiceTypeRepository;
import com.aykhedma.repository.UserRepository;
import com.aykhedma.mapper.ProviderMapper;
import com.aykhedma.mapper.UserMapper;
import com.aykhedma.model.location.Location;
import com.aykhedma.model.notification.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminServiceImpl Unit Tests")
class AdminServiceImplTest {

    @Mock private ProviderRepository providerRepository;
    @Mock private ProviderMapper providerMapper;
    @Mock private ProviderService providerService;
    @Mock private NotificationFactory notificationFactory;
    @Mock private ConsumerRepository consumerRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private EmergencyRequestRepository emergencyRequestRepository;
    @Mock private InteractionRatingRepository interactionRatingRepository;
    @Mock private ProviderResponseRepository providerResponseRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationPreferenceRepository notificationPreferenceRepository;
    @Mock private DeviceTokenRepository deviceTokenRepository;
    @Mock private ChatSessionRepository chatSessionRepository;
    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserRepository userRepository;
    @Mock private ServiceTypeRepository serviceTypeRepository;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminServiceImpl adminService;

    @Test
    @DisplayName("deleteUser should purge provider-linked data before deleting the provider")
    void deleteUser_providerPurgesRelatedData() {
        Long userId = 10L;
        Provider provider = buildProvider(userId);
        Consumer otherConsumer = buildConsumer(11L);
        ChatRoom chatRoom = ChatRoom.builder()
                .id("room-1")
                .participants(new ArrayList<>(List.of(provider, otherConsumer)))
                .isActive(true)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(provider));
        when(chatRoomRepository.findAllByParticipant(provider)).thenReturn(List.of(chatRoom));

        assertThatCode(() -> adminService.deleteUser(userId)).doesNotThrowAnyException();

        InOrder inOrder = inOrder(
                notificationPreferenceRepository,
                notificationRepository,
                deviceTokenRepository,
                refreshTokenRepository,
                chatSessionRepository,
                chatRoomRepository,
                userRepository,
                bookingRepository,
                emergencyRequestRepository,
                interactionRatingRepository,
                providerResponseRepository,
                documentRepository);

        inOrder.verify(notificationPreferenceRepository).deleteByUserId(userId);
        inOrder.verify(notificationRepository).deleteByUserId(userId);
        inOrder.verify(deviceTokenRepository).deleteByUserId(userId);
        inOrder.verify(refreshTokenRepository).deleteByUser(provider);
        inOrder.verify(chatSessionRepository).deleteByUserId(userId);
        inOrder.verify(chatRoomRepository).findAllByParticipant(same(provider));
        inOrder.verify(chatRoomRepository).deleteAll(List.of(chatRoom));
        inOrder.verify(userRepository).deleteSavedProviderLinks(userId);
        inOrder.verify(bookingRepository).deleteByProviderId(userId);
        inOrder.verify(emergencyRequestRepository).deleteBySelectedProviderId(userId);
        inOrder.verify(interactionRatingRepository).deleteByProviderId(userId);
        inOrder.verify(providerResponseRepository).deleteByProviderId(userId);
        inOrder.verify(documentRepository).deleteByProviderId(userId);
        inOrder.verify(userRepository).delete(provider);
    }

    @Test
    @DisplayName("deleteUser should purge consumer-linked data before deleting the consumer")
    void deleteUser_consumerPurgesRelatedData() {
        Long userId = 20L;
        Consumer consumer = buildConsumer(userId);
        Provider otherProvider = buildProvider(21L);
        ChatRoom chatRoom = ChatRoom.builder()
                .id("room-2")
                .participants(new ArrayList<>(List.of(consumer, otherProvider)))
                .isActive(true)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(consumer));
        when(chatRoomRepository.findAllByParticipant(consumer)).thenReturn(List.of(chatRoom));

        assertThatCode(() -> adminService.deleteUser(userId)).doesNotThrowAnyException();

        verify(notificationPreferenceRepository).deleteByUserId(userId);
        verify(notificationRepository).deleteByUserId(userId);
        verify(deviceTokenRepository).deleteByUserId(userId);
        verify(refreshTokenRepository).deleteByUser(consumer);
        verify(chatSessionRepository).deleteByUserId(userId);
        verify(chatRoomRepository).deleteAll(List.of(chatRoom));
        verify(userRepository).deleteSavedProviderLinks(userId);
        verify(bookingRepository).deleteByConsumerId(userId);
        verify(emergencyRequestRepository).deleteByConsumerId(userId);
        verify(interactionRatingRepository).deleteByConsumerId(userId);
        verify(userRepository).delete(consumer);
    }

    private Consumer buildConsumer(Long id) {
        return Consumer.builder()
                .id(id)
                .name("Consumer " + id)
                .email("consumer" + id + "@example.com")
                .phoneNumber("0101234567" + (id % 10))
                .password("encoded-password")
                .role(UserType.CONSUMER)
                .enabled(true)
                .credentialsNonExpired(true)
                .averageRating(0.0)
                .totalBookings(0)
                .cancelledBookings(0)
                .build();
    }

    private Provider buildProvider(Long id) {
        ServiceType serviceType = ServiceType.builder()
                .id(1L)
                .name("Plumbing")
                .riskLevel(RiskLevel.LOW)
                .defaultPriceType(PriceType.HOUR)
                .basePrice(100.0)
                .estimatedDuration(60)
                .build();

        Location location = Location.builder()
                .id(1L)
                .build();

        return Provider.builder()
                .id(id)
                .name("Provider " + id)
                .email("provider" + id + "@example.com")
                .phoneNumber("0101234568" + (id % 10))
                .password("encoded-password")
                .role(UserType.PROVIDER)
                .enabled(true)
                .credentialsNonExpired(true)
                .serviceType(serviceType)
                .location(location)
                .verificationStatus(VerificationStatus.VERIFIED)
                .price(100.0)
                .priceType(PriceType.HOUR)
                .nationalId("12345678901234")
                .schedule(null)
                .build();
    }
}
