package com.aykhedma.controller;

import com.aykhedma.dto.request.RatingRequest;
import com.aykhedma.dto.request.EmergencyRatingRequest;
import com.aykhedma.dto.request.ProviderEmergencyRatingRequest;
import com.aykhedma.dto.response.BookingResponse;
import com.aykhedma.dto.response.EmergencyRequestResponse;
import com.aykhedma.dto.response.EvaluationQuestionResponse;
import com.aykhedma.service.BookingService;
import com.aykhedma.service.EmergencyRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/ratings")
@RequiredArgsConstructor
public class RatingController {

    private final BookingService bookingService;
    private final EmergencyRequestService emergencyRequestService;
    
    @GetMapping("/questions")
    public ResponseEntity<List<EvaluationQuestionResponse>> getEvaluationQuestions(
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage,
            @RequestParam(value = "lang", required = false) String lang) {
        
        boolean isArabic = false;
        if (lang != null && lang.trim().equalsIgnoreCase("ar")) {
            isArabic = true;
        } else if (acceptLanguage != null && acceptLanguage.toLowerCase().startsWith("ar")) {
            isArabic = true;
        }

        List<EvaluationQuestionResponse> questions;
        if (isArabic) {
            questions = Arrays.asList(
                new EvaluationQuestionResponse("الالتزام بالوقت", "ما مدى التزام مزود الخدمة بالوصول في الوقت المحدد؟", 1, 5),
                new EvaluationQuestionResponse("الالتزام والجدية", "هل أظهر مزود الخدمة تفانياً والتزاماً بالمهمة المطلوبة؟", 1, 5),
                new EvaluationQuestionResponse("جودة العمل", "كيف تقيم الجودة العامة للخدمة المقدمة؟", 1, 5)
            );
        } else {
            questions = Arrays.asList(
                new EvaluationQuestionResponse("Punctuality", "How punctual was the provider in arriving the scheduled time?", 1, 5),
                new EvaluationQuestionResponse("Commitment", "Did the provider demonstrate dedication and commitment to the requested task?", 1, 5),
                new EvaluationQuestionResponse("Quality of Work", "How would you rate the overall quality of the service provided?", 1, 5)
            );
        }
        return ResponseEntity.ok(questions);
    }

    @PostMapping
    @PreAuthorize("hasRole('CONSUMER')")
    public ResponseEntity<BookingResponse> submitRating(
            @AuthenticationPrincipal(expression = "user.id") Long consumerId,
            @Valid @RequestBody RatingRequest ratingRequest) {
        
        BookingResponse response = bookingService.submitRating(consumerId, ratingRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/consumer")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<BookingResponse> submitConsumerRating(
            @AuthenticationPrincipal(expression = "user.id") Long providerId,
            @Valid @RequestBody com.aykhedma.dto.request.ProviderRatingRequest ratingRequest) {
        
        BookingResponse response = bookingService.submitConsumerRating(providerId, ratingRequest);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/consumer/{consumerId}")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<List<com.aykhedma.dto.response.ConsumerReviewResponse>> getConsumerReviews(
            @PathVariable Long consumerId) {
        return ResponseEntity.ok(bookingService.getConsumerReviews(consumerId));
    }

    @PostMapping("/emergency")
    @PreAuthorize("hasRole('CONSUMER')")
    public ResponseEntity<EmergencyRequestResponse> submitEmergencyRating(
            @AuthenticationPrincipal(expression = "user.id") Long consumerId,
            @Valid @RequestBody EmergencyRatingRequest ratingRequest) {
        
        EmergencyRequestResponse response = emergencyRequestService.submitEmergencyRequestRating(consumerId, ratingRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/emergency/consumer")
    @PreAuthorize("hasRole('PROVIDER')")
    public ResponseEntity<EmergencyRequestResponse> submitConsumerEmergencyRating(
            @AuthenticationPrincipal(expression = "user.id") Long providerId,
            @Valid @RequestBody ProviderEmergencyRatingRequest ratingRequest) {
        
        EmergencyRequestResponse response = emergencyRequestService.submitConsumerEmergencyRequestRating(providerId, ratingRequest);
        return ResponseEntity.ok(response);
    }
}
