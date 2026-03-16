package com.aykhedma.auth;

import com.aykhedma.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class OtpService {

    private final NotificationService notificationService;
    private final Map<String, String> otpStorage = new ConcurrentHashMap<>();

    public String generateOtp(String email) {
        String normalizedEmail = normalizeEmail(email);
        String otp = String.valueOf((int) (Math.random() * 900000) + 100000);

        otpStorage.put(normalizedEmail, otp.trim());
        notificationService.sendOtpEmail(normalizedEmail, otp.trim());

        return otp.trim();
    }

    public boolean validateOtp(String email, String otp) {
        if (email == null || otp == null) {
            return false;
        }

        String normalizedEmail = normalizeEmail(email);
        String providedOtp = otp.trim();

        String storedOtp = otpStorage.get(normalizedEmail);

        if (storedOtp == null) {
            return false;
        }

        boolean matches = storedOtp.equals(providedOtp);

        if (matches) {
            otpStorage.remove(normalizedEmail);
        }

        return matches;
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

}
