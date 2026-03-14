package com.aykhedma.Auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class OtpService {

    private final Map<String, String> otpStorage = new ConcurrentHashMap<>();

    public void generateOtp(String phone) {
        String otp = String.valueOf((int)(Math.random() * 900000) + 100000);

        otpStorage.put(phone.trim(), otp.trim());

        System.out.println("OTP for " + phone + ": " + otp);
    }


    public boolean validateOtp(String phone, String otp) {

        String storedOtp = otpStorage.get(phone);

        if (storedOtp == null) {
            return false;
        }

        return storedOtp.trim().equals(otp.trim());
    }



}
