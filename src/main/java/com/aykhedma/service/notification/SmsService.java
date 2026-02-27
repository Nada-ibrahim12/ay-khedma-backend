//package com.aykhedma.service.notification;
//
//import com.twilio.Twilio;
//import com.twilio.rest.api.v2010.account.Message;
//import com.twilio.type.PhoneNumber;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.scheduling.annotation.Async;
//import org.springframework.stereotype.Service;
//import jakarta.annotation.PostConstruct;
//
//@Service
//@Slf4j
//public class SmsService {
//
//    @Value("${twilio.account-sid:}")
//    private String accountSid;
//
//    @Value("${twilio.auth-token:}")
//    private String authToken;
//
//    @Value("${twilio.phone-number:}")
//    private String fromNumber;
//
//    private boolean isTwilioEnabled = false;
//
//    @PostConstruct
//    public void init() {
//        if (accountSid != null && !accountSid.isEmpty() &&
//                authToken != null && !authToken.isEmpty()) {
//            Twilio.init(accountSid, authToken);
//            isTwilioEnabled = true;
//            log.info("Twilio initialized successfully");
//        } else {
//            log.warn("Twilio credentials not configured. SMS service disabled.");
//        }
//    }
//
//    @Async
//    public void sendSms(String to, String message) {
//        if (!isTwilioEnabled) {
//            log.warn("Twilio not configured. SMS not sent to: {}", to);
//            return;
//        }
//
//        try {
//            // Ensure phone number has country code
//            String formattedNumber = formatPhoneNumber(to);
//
//            Message smsMessage = Message.creator(
//                    new PhoneNumber(formattedNumber),
//                    new PhoneNumber(fromNumber),
//                    message
//            ).create();
//
//            log.info("SMS sent to {}: SID {}", formattedNumber, smsMessage.getSid());
//
//        } catch (Exception e) {
//            log.error("Failed to send SMS to {}: {}", to, e.getMessage());
//        }
//    }
//
//    @Async
//    public void sendOtp(String to, String otpCode) {
//        String message = String.format(
//                "Your Ay Khedma verification code is: %s. Valid for 10 minutes.",
//                otpCode
//        );
//        sendSms(to, message);
//    }
//
//    private String formatPhoneNumber(String phone) {
//        // Remove any non-digit characters
//        String digits = phone.replaceAll("\\D", "");
//
//        // Add country code if missing (assuming Egypt +20)
//        if (!digits.startsWith("20") && digits.length() == 10) {
//            return "+20" + digits;
//        }
//        return "+" + digits;
//    }
//}