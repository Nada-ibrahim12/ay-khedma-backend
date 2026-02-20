package com.aykhedma.controller;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.request.ProviderProfileRequest;
import com.aykhedma.dto.request.WorkingDayRequest;
import com.aykhedma.dto.response.*;
import com.aykhedma.service.ProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/providers")
@RequiredArgsConstructor
public class ProviderController {

    private final ProviderService providerService;

    // ===== Profile Management =====

    @GetMapping("/{providerId}")
    @Operation(summary = "Get provider profile")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved provider profile",
                    content = @Content(schema = @Schema(implementation = ProviderResponse.class))),
            @ApiResponse(responseCode = "404", description = "Provider not found")
    })
    public ResponseEntity<ProviderResponse> getProviderProfile(
            @Parameter(description = "ID of the provider", required = true, example = "1")
            @PathVariable Long providerId) {
        ProviderResponse response = providerService.getProviderProfile(providerId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{providerId}")
    @Operation(summary = "Update provider profile")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully updated provider profile",
                    content = @Content(schema = @Schema(implementation = ProviderResponse.class))),
            @ApiResponse(responseCode = "404", description = "Provider not found"),
            @ApiResponse(responseCode = "400", description = "Invalid input data")
    })
    public ResponseEntity<ProviderResponse> updateProviderProfile(
            @Parameter(description = "ID of the provider", required = true, example = "1")
            @PathVariable Long providerId,

            @Parameter(description = "Updated provider profile data", required = true)
            @Valid @RequestBody ProviderProfileRequest request) {
        ProviderResponse response = providerService.updateProviderProfile(providerId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/{providerId}/profile-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Update profile picture")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully updated profile picture",
                    content = @Content(schema = @Schema(implementation = ProviderResponse.class))),
            @ApiResponse(responseCode = "404", description = "Provider not found"),
            @ApiResponse(responseCode = "400", description = "Invalid file format or size")
    })
    public ResponseEntity<ProviderResponse> updateProfilePicture(
            @Parameter(description = "ID of the provider", required = true, example = "1")
            @PathVariable Long providerId,

            @Parameter(description = "Profile picture file (max 5MB, supported formats: JPEG, PNG, GIF)", required = true)
            @RequestParam("file") MultipartFile file) throws IOException {
        ProviderResponse response = providerService.updateProfilePicture(providerId, file);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{providerId}/profile-picture")
    @Operation(summary = "Delete profile picture")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Successfully deleted profile picture"),
            @ApiResponse(responseCode = "404", description = "Provider not found")
    })
    public ResponseEntity<Void> deleteProfilePicture(
            @Parameter(description = "ID of the provider", required = true, example = "1")
            @PathVariable Long providerId) throws IOException {
        providerService.updateProfilePicture(providerId, null);
        return ResponseEntity.noContent().build();
    }

    // ===== Location Management =====

    @PatchMapping("/{providerId}/location")
    @Operation(summary = "Update provider location")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully updated location",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "404", description = "Provider not found"),
            @ApiResponse(responseCode = "400", description = "Invalid location data")
    })
    public ResponseEntity<ProfileResponse> updateProviderLocation(
            @Parameter(description = "ID of the provider", required = true, example = "1")
            @PathVariable Long providerId,

            @Parameter(description = "Location data", required = true)
            @Valid @RequestBody LocationDTO request) {
        ProfileResponse response = providerService.updateProviderLocation(providerId, request);
        return ResponseEntity.ok(response);
    }

    // ===== Schedule Management =====

    @GetMapping("/{providerId}/schedule")
    @Operation(summary = "Get provider schedule")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved schedule",
                    content = @Content(schema = @Schema(implementation = ScheduleResponse.class))),
            @ApiResponse(responseCode = "404", description = "Provider not found")
    })
    public ResponseEntity<ScheduleResponse> getSchedule(
            @Parameter(description = "ID of the provider", required = true, example = "1")
            @PathVariable Long providerId) {
        ScheduleResponse response = providerService.getSchedule(providerId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{providerId}/schedule/working-days")
    @Operation(summary = "Add working day")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Successfully added working day",
                    content = @Content(schema = @Schema(implementation = ScheduleResponse.class))),
            @ApiResponse(responseCode = "404", description = "Provider not found"),
            @ApiResponse(responseCode = "400", description = "Invalid working day data or duplicate")
    })
    public ResponseEntity<ScheduleResponse> addWorkingDay(
            @Parameter(description = "ID of the provider", required = true, example = "1")
            @PathVariable Long providerId,

            @Parameter(description = "Working day data", required = true)
            @Valid @RequestBody WorkingDayRequest request) {
        ScheduleResponse response = providerService.addWorkingDay(providerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{providerId}/schedule/working-days/{workingDayId}")
    @Operation(summary = "Remove working day")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully removed working day",
                    content = @Content(schema = @Schema(implementation = ScheduleResponse.class))),
            @ApiResponse(responseCode = "404", description = "Provider or working day not found")
    })
    public ResponseEntity<ScheduleResponse> removeWorkingDay(
            @Parameter(description = "ID of the provider", required = true, example = "1")
            @PathVariable Long providerId,

            @Parameter(description = "ID of the working day", required = true, example = "1")
            @PathVariable Long workingDayId) {
        ScheduleResponse response = providerService.removeWorkingDay(providerId, workingDayId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{providerId}/available-slots")
    @Operation(summary = "Get available time slots")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved time slots"),
            @ApiResponse(responseCode = "404", description = "Provider not found")
    })
    public ResponseEntity<List<LocalTime>> getAvailableTimeSlots(
            @Parameter(description = "ID of the provider", required = true, example = "1")
            @PathVariable Long providerId,

            @Parameter(description = "Date (format: yyyy-MM-dd)", required = true, example = "2026-03-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<LocalTime> response = providerService.getAvailableTimeSlots(providerId, date);
        return ResponseEntity.ok(response);
    }

    // ===== Document Management =====

    @PostMapping(value = "/{providerId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload document")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Successfully uploaded document",
                    content = @Content(schema = @Schema(implementation = DocumentResponse.class))),
            @ApiResponse(responseCode = "404", description = "Provider not found"),
            @ApiResponse(responseCode = "400", description = "Invalid file format or size")
    })
    public ResponseEntity<DocumentResponse> uploadDocument(
            @Parameter(description = "ID of the provider", required = true, example = "1")
            @PathVariable Long providerId,

            @Parameter(description = "Document file (max 10MB, supported formats: PDF, DOC, DOCX, XLS, XLSX, TXT)", required = true)
            @RequestParam("file") MultipartFile file,

            @Parameter(description = "Document type (e.g., LICENSE, CERTIFICATE, ID)", required = true, example = "LICENSE")
            @RequestParam String documentType) throws IOException {
        DocumentResponse response = providerService.uploadDocument(providerId, file, documentType);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{providerId}/documents")
    @Operation(summary = "Get provider documents")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved documents",
                    content = @Content(schema = @Schema(implementation = DocumentResponse.class))),
            @ApiResponse(responseCode = "404", description = "Provider not found")
    })
    public ResponseEntity<List<DocumentResponse>> getProviderDocuments(
            @Parameter(description = "ID of the provider", required = true, example = "1")
            @PathVariable Long providerId) {
        List<DocumentResponse> response = providerService.getProviderDocuments(providerId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{providerId}/documents/{documentId}")
    @Operation(summary = "Delete document")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully deleted document",
                    content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
            @ApiResponse(responseCode = "404", description = "Provider or document not found")
    })
    public ResponseEntity<ProfileResponse> deleteDocument(
            @Parameter(description = "ID of the provider", required = true, example = "1")
            @PathVariable Long providerId,

            @Parameter(description = "ID of the document", required = true, example = "1")
            @PathVariable Long documentId) {
        ProfileResponse response = providerService.deleteDocument(providerId, documentId);
        return ResponseEntity.ok(response);
    }

    // ===== Search & Discovery =====

    @GetMapping("/nearby")
    @Operation(summary = "Find nearby providers")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully found providers",
                    content = @Content(schema = @Schema(implementation = ProviderSummaryResponse.class)))
    })
    public ResponseEntity<List<ProviderSummaryResponse>> findNearbyProviders(
            @Parameter(description = "Latitude", required = true, example = "30.0444")
            @RequestParam Double latitude,

            @Parameter(description = "Longitude", required = true, example = "31.2357")
            @RequestParam Double longitude,

            @Parameter(description = "Service type ID", required = true, example = "1")
            @RequestParam Long serviceTypeId,

            @Parameter(description = "Search radius in km", required = true, example = "10")
            @RequestParam Double radius) {
        List<ProviderSummaryResponse> response = providerService.findNearbyProviders(latitude, longitude, serviceTypeId, radius);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    @Operation(summary = "Search providers")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully found providers",
                    content = @Content(schema = @Schema(implementation = ProviderSummaryResponse.class)))
    })
    public ResponseEntity<List<ProviderSummaryResponse>> searchProviders(
            @Parameter(description = "Search keyword", example = "plumber")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "Category ID", example = "1")
            @RequestParam(required = false) Long categoryId,

            @Parameter(description = "Minimum rating", example = "4.0")
            @RequestParam(required = false) Double minRating) {
        List<ProviderSummaryResponse> response = providerService.searchProviders(keyword, categoryId, minRating);
        return ResponseEntity.ok(response);
    }

    // ===== Verification & Status =====

    @GetMapping("/{providerId}/verification-status")
    @Operation(summary = "Get verification status")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved status",
                    content = @Content(schema = @Schema(implementation = ProviderResponse.class))),
            @ApiResponse(responseCode = "404", description = "Provider not found")
    })
    public ResponseEntity<ProviderResponse> getVerificationStatus(
            @Parameter(description = "ID of the provider", required = true, example = "1")
            @PathVariable Long providerId) {
        ProviderResponse response = providerService.getVerificationStatus(providerId);
        return ResponseEntity.ok(response);
    }
//
//    @PatchMapping("/{providerId}/emergency-status")
//    @Operation(summary = "Update emergency status", description = "Enables or disables emergency mode for a provider")
//    @ApiResponses(value = {
//            @ApiResponse(responseCode = "200", description = "Successfully updated emergency status",
//                    content = @Content(schema = @Schema(implementation = ProviderResponse.class))),
//            @ApiResponse(responseCode = "404", description = "Provider not found")
//    })
//    public ResponseEntity<ProviderResponse> updateEmergencyStatus(
//            @Parameter(description = "ID of the provider", required = true, example = "1")
//            @PathVariable Long providerId,
//
//            @Parameter(description = "Emergency status", required = true, example = "true")
//            @RequestParam boolean enabled) {
//        // Uncomment this method in your service first
//        ProviderResponse response = providerService.updateEmergencyStatus(providerId, enabled);
//        return ResponseEntity.ok(response);
//    }

    @GetMapping("/{providerId}/exists")
    @Operation(summary = "Check if provider exists")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Provider exists"),
            @ApiResponse(responseCode = "404", description = "Provider does not exist")
    })
    public ResponseEntity<Void> checkProviderExists(
            @Parameter(description = "ID of the provider", required = true, example = "1")
            @PathVariable Long providerId) {
        try {
            providerService.getProviderProfile(providerId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}