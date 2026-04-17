package com.aykhedma.controller;

import com.aykhedma.dto.request.InteractionRatingRequest;
import com.aykhedma.dto.response.InteractionRatingResponse;
import com.aykhedma.security.CustomUserDetails;
import com.aykhedma.service.InteractionRatingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ratings/interaction")
@RequiredArgsConstructor
public class InteractionRatingController {

    private final InteractionRatingService interactionRatingService;

    @PostMapping
    public ResponseEntity<InteractionRatingResponse> submitRating(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody InteractionRatingRequest request) {
        return ResponseEntity.ok(interactionRatingService.submitRating(currentUser.getUser().getId(), request));
    }

    @GetMapping("/provider/{providerId}")
    public ResponseEntity<List<InteractionRatingResponse>> getProviderRatings(@PathVariable Long providerId) {
        return ResponseEntity.ok(interactionRatingService.getProviderRatings(providerId));
    }

    @GetMapping("/me")
    public ResponseEntity<List<InteractionRatingResponse>> getMyRatings(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        return ResponseEntity.ok(interactionRatingService.getProviderRatings(currentUser.getUser().getId()));
    }
}
