package com.aykhedma;

import com.aykhedma.config.TestSecurityConfig;
import com.aykhedma.exception.GlobalExceptionHandler;
import com.aykhedma.mapper.ConsumerMapper;
import com.aykhedma.mapper.ProviderMapper;
import com.aykhedma.repository.*;
import com.aykhedma.security.CustomUserDetailsService;
import com.aykhedma.security.JwtService;
import com.aykhedma.service.*;
import com.aykhedma.service.notification.EmailService;
import com.aykhedma.service.NotificationFactory;
import com.aykhedma.service.verification.VerificationService; 
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.reactive.function.client.WebClient; 

@Import({ TestSecurityConfig.class, GlobalExceptionHandler.class })
public abstract class BaseWebMvcTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockBean
    protected ConsumerService consumerService;

    @MockBean
    protected ProviderService providerService;

    @MockBean
    protected BookingService bookingService;

    @MockBean
    protected JwtService jwtService;

    @MockBean
    protected CustomUserDetailsService customUserDetailsService;

    @MockBean
    protected FileStorageService fileStorageService;

    @MockBean
    protected LocationService locationService;

    @MockBean
    protected NotificationService notificationService;

    @MockBean
    protected ChatService chatService;

    @MockBean
    protected EmailService emailService;

    @MockBean
    protected NotificationFactory notificationFactory;

    @MockBean
    protected GoogleMapsService googleMapsService;

    @MockBean
    protected ServiceManagementService serviceManagementService;

    @MockBean
    protected ServiceCategoryService serviceCategoryService;

    @MockBean
    protected VerificationService verificationService;

    @MockBean
    protected WebClient.Builder webClientBuilder;

    @MockBean
    protected UserRepository userRepository;

    @MockBean
    protected DocumentRepository documentRepository;

    @MockBean
    protected ConsumerRepository consumerRepository;

    @MockBean
    protected ProviderRepository providerRepository;

    @MockBean
    protected ServiceTypeRepository serviceTypeRepository;

    @MockBean
    protected ServiceCategoryRepository serviceCategoryRepository;

    @MockBean
    protected BookingRepository bookingRepository;

    @MockBean
    protected NotificationRepository notificationRepository;

    @MockBean
    protected RefreshTokenRepository refreshTokenRepository;

    @MockBean
    protected ChatRoomRepository chatRoomRepository;

    @MockBean
    protected ChatMessageRepository chatMessageRepository;

    @MockBean
    protected LocationRepository locationRepository;

    @MockBean
    protected TimeSlotRepository timeSlotRepository;

    @MockBean
    protected EmergencyRequestRepository emergencyRequestRepository;

    @MockBean
    protected ScheduleRepository scheduleRepository;

    @MockBean
    protected ConsumerMapper consumerMapper;

    @MockBean
    protected ProviderMapper providerMapper;
}