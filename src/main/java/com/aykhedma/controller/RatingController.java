package com.aykhedma.controller;

import com.aykhedma.dto.request.RatingRequest;
import com.aykhedma.dto.response.BookingResponse;
import com.aykhedma.dto.response.EvaluationQuestionResponse;
import com.aykhedma.service.BookingService;
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
    
    @GetMapping("/questions")
    public ResponseEntity<List<EvaluationQuestionResponse>> getEvaluationQuestions() {
        List<EvaluationQuestionResponse> questions = Arrays.asList(
            new EvaluationQuestionResponse("Punctuality", "How punctual was the provider in arriving the scheduled time?", 1, 5),
            new EvaluationQuestionResponse("Commitment", "Did the provider demonstrate dedication and commitment to the requested task?", 1, 5),
            new EvaluationQuestionResponse("Quality of Work", "How would you rate the overall quality of the service provided?", 1, 5)
        );
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
}
