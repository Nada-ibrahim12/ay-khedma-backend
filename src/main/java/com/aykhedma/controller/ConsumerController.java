package com.aykhedma.controller;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.request.ConsumerProfileRequest;
import com.aykhedma.dto.response.ConsumerResponse;
import com.aykhedma.dto.response.ProfileResponse;
import com.aykhedma.dto.response.ProviderSummaryResponse;
import com.aykhedma.service.ConsumerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/consumers")
@RequiredArgsConstructor
public class ConsumerController {

    private final ConsumerService consumerService;

    @GetMapping("/{consumerId}")
    @Operation(summary = "Get consumer profile")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved consumer profile",
                    content = @Content(schema = @Schema(implementation = ConsumerResponse.class))),
            @ApiResponse(responseCode = "404", description = "Consumer not found"),
            @ApiResponse(responseCode = "400", description = "Invalid consumer ID supplied")
    })
    public ResponseEntity<ConsumerResponse> getConsumerProfile(
            @Parameter(description = "ID of the consumer to retrieve", required = true)
            @PathVariable Long consumerId) {
        ConsumerResponse response = consumerService.getConsumerProfile(consumerId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{consumerId}")
    @Operation(summary = "Update consumer profile")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully updated consumer profile",
                    content = @Content(schema = @Schema(implementation = ConsumerResponse.class))),
            @ApiResponse(responseCode = "404", description = "Consumer not found"),
            @ApiResponse(responseCode = "400", description = "Invalid input data")
    })
    public ResponseEntity<ConsumerResponse> updateConsumerProfile(
            @Parameter(description = "ID of the consumer to update", required = true)
            @PathVariable Long consumerId,

            @Parameter(description = "Updated consumer profile data", required = true)
            @Valid @RequestBody ConsumerProfileRequest request) {
        ConsumerResponse response = consumerService.updateConsumerProfile(consumerId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/{consumerId}/profile-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Update profile picture")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully updated profile picture",
                    content = @Content(schema = @Schema(implementation = ConsumerResponse.class))),
            @ApiResponse(responseCode = "404", description = "Consumer not found"),
            @ApiResponse(responseCode = "400", description = "Invalid file format or size"),
            @ApiResponse(responseCode = "413", description = "File too large"),
            @ApiResponse(responseCode = "415", description = "Unsupported media type")
    })
    public ResponseEntity<ConsumerResponse> updateProfilePicture(
            @Parameter(description = "ID of the consumer", required = true)
            @PathVariable Long consumerId,

            @Parameter(description = "Profile picture file (max 5MB, supported formats: JPEG, PNG, GIF)", required = true)
            @RequestParam("file") MultipartFile file) throws IOException {
        ConsumerResponse response = consumerService.updateProfilePicture(consumerId, file);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{consumerId}/saved-providers/{providerId}")
    @Operation(summary = "Save a provider to favorites")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully saved provider",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "404", description = "Consumer or provider not found"),
            @ApiResponse(responseCode = "409", description = "Provider already saved")
    })
    public ResponseEntity<ProfileResponse> saveProvider(
            @Parameter(description = "ID of the consumer", required = true)
            @PathVariable Long consumerId,

            @Parameter(description = "ID of the provider to save", required = true)
            @PathVariable Long providerId) {
        ProfileResponse response = consumerService.saveProvider(consumerId, providerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{consumerId}/saved-providers/{providerId}")
    @Operation(summary = "Remove saved provider from favorites")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully removed provider",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "404", description = "Consumer not found")
    })
    public ResponseEntity<ProfileResponse> removeSavedProvider(
            @Parameter(description = "ID of the consumer", required = true)
            @PathVariable Long consumerId,

            @Parameter(description = "ID of the provider to remove", required = true)
            @PathVariable Long providerId) {
        ProfileResponse response = consumerService.removeSavedProvider(consumerId, providerId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{consumerId}/saved-providers")
    @Operation(summary = "Get saved/favorites providers")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved saved providers",
                    content = @Content(schema = @Schema(implementation = ProviderSummaryResponse.class))),
            @ApiResponse(responseCode = "404", description = "Consumer not found")
    })
    public ResponseEntity<List<ProviderSummaryResponse>> getSavedProviders(
            @Parameter(description = "ID of the consumer", required = true)
            @PathVariable Long consumerId) {
        List<ProviderSummaryResponse> responses = consumerService.getSavedProviders(consumerId);
        return ResponseEntity.ok(responses);
    }

    @PatchMapping("/{consumerId}/location")
    @Operation(summary = "Update consumer location")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully updated location",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "404", description = "Consumer not found"),
            @ApiResponse(responseCode = "400", description = "Invalid location data")
    })
    public ResponseEntity<ProfileResponse> updateLocation(
            @Parameter(description = "ID of the consumer", required = true)
            @PathVariable Long consumerId,

            @Parameter(description = "Location data (latitude, longitude, address)", required = true)
            @Valid @RequestBody LocationDTO request) {
        ProfileResponse response = consumerService.updateLocation(consumerId, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{consumerId}/profile-picture")
    @Operation(summary = "Delete profile picture")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Successfully deleted profile picture"),
            @ApiResponse(responseCode = "404", description = "Consumer not found or no profile picture exists")
    })
    public ResponseEntity<Void> deleteProfilePicture(
            @Parameter(description = "ID of the consumer", required = true)
            @PathVariable Long consumerId) throws IOException {
        consumerService.deleteProfilePicture(consumerId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{consumerId}/exists")
    @Operation(summary = "Check if consumer exists")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Consumer exists"),
            @ApiResponse(responseCode = "404", description = "Consumer does not exist")
    })
    public ResponseEntity<Void> checkConsumerExists(
            @Parameter(description = "ID of the consumer to check", required = true)
            @PathVariable Long consumerId) {
        try {
            consumerService.getConsumerProfile(consumerId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}