package com.aykhedma;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.aykhedma.service.FileStorageService;
import com.aykhedma.service.GoogleMapsService;
import com.aykhedma.service.NotificationFactory;
import com.aykhedma.service.notification.EmailService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

    @MockBean
    private JavaMailSender javaMailSender;

    @MockBean
    private GoogleMapsService googleMapsService;

    @MockBean
    private NotificationFactory notificationFactory;

    @MockBean
    private FileStorageService fileStorageService;

    @MockBean
    private EmailService emailService;

    @org.junit.jupiter.api.BeforeEach
    void setUpGoogleMapsMock() {
        GoogleMapsService.LocationDetails locationDetails = new GoogleMapsService.LocationDetails(
                "123 Test Street", 
                "Cairo", 
                "Egypt", 
                "123 Test Street, Cairo, Egypt", 
                "test_place_id_123", 
                "30.0444", 
                "31.2357", 
                "123 Test Street, Cairo, Egypt" 
        );

        when(googleMapsService.getLocationDetails(anyDouble(), anyDouble()))
                .thenReturn(locationDetails);
    }
}