package com.aykhedma.controller;

import com.aykhedma.dto.request.ProviderProfileRequest;
import com.aykhedma.dto.request.WorkingDayRequest;
import com.aykhedma.dto.response.*;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.model.user.VerificationStatus;
import com.aykhedma.service.ProviderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

        @PreAuthorize("hasAnyRole('CONSUMER', 'ADMIN')")
        @GetMapping("/{providerId}")
        @Operation(summary = "Get provider profile")
        @ApiResponses(value = {
                @ApiResponse(responseCode = "200", description = "Successfully retrieved provider profile", content = @Content(schema = @Schema(implementation = ProviderResponse.class))),
                @ApiResponse(responseCode = "404", description = "Provider not found")
        })
        public ResponseEntity<ProviderResponse> getProviderProfile(
                @Parameter(description = "ID of the provider", required = true) @PathVariable Long providerId) {
                ProviderResponse response = providerService.getProviderProfile(providerId);
                return ResponseEntity.ok(response);
        }

        @PreAuthorize("hasAnyRole('PROVIDER')")
        @GetMapping("/me")
        @Operation(summary = "Get my provider profile")
        @ApiResponses(value = {
                @ApiResponse(responseCode = "200", description = "Successfully retrieved provider profile", content = @Content(schema = @Schema(implementation = ProviderResponse.class))),
                @ApiResponse(responseCode = "404", description = "Provider not found")
        })
        public ResponseEntity<ProviderResponse> getmYProviderProfile(
                @Parameter(description = "ID of the provider", required = true) @AuthenticationPrincipal(expression = "user.id") Long providerId) {
                ProviderResponse response = providerService.getProviderProfile(providerId);
                return ResponseEntity.ok(response);
        }

        @PreAuthorize("hasAnyRole('PROVIDER')")
        @PutMapping("/me")
        @Operation(summary = "Update my profile")
        @ApiResponses(value = {
                @ApiResponse(responseCode = "200", description = "Successfully updated provider profile", content = @Content(schema = @Schema(implementation = ProviderResponse.class))),
                @ApiResponse(responseCode = "404", description = "Provider not found"),
                @ApiResponse(responseCode = "400", description = "Invalid input data")
        })
        public ResponseEntity<ProviderResponse> updateProviderProfile(
                @AuthenticationPrincipal(expression = "user.id") Long providerId,
                @Parameter(description = "Updated provider profile data", required = true) @Valid @RequestBody ProviderProfileRequest request) {
                ProviderResponse response = providerService.updateProviderProfile(providerId, request);
                return ResponseEntity.ok(response);
        }

        @PreAuthorize("hasRole('PROVIDER')")
        @PostMapping(value = "/me/profile-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @Operation(summary = "Update my profile picture")
        @ApiResponses(value = {
                @ApiResponse(responseCode = "200", description = "Successfully updated profile picture", content = @Content(schema = @Schema(implementation = ProviderResponse.class))),
                @ApiResponse(responseCode = "404", description = "Provider not found"),
                @ApiResponse(responseCode = "400", description = "Invalid file format or size")
        })
        public ResponseEntity<ProviderResponse> updateProfilePicture(
                @AuthenticationPrincipal(expression = "user.id") Long providerId,

                @Parameter(description = "Profile picture file (max 5MB, supported formats: JPEG, PNG, GIF)", required = true) @RequestParam("file") MultipartFile file)
                throws IOException {
                ProviderResponse response = providerService.updateProfilePicture(providerId, file);
                return ResponseEntity.ok(response);
        }

        // @DeleteMapping("/{providerId}/profile-picture")
        // @Operation(summary = "Delete profile picture")
        // @ApiResponses(value = {
        // @ApiResponse(responseCode = "204", description = "Successfully deleted
        // profile picture"),
        // @ApiResponse(responseCode = "404", description = "Provider not found")
        // })
        // public ResponseEntity<Void> deleteProfilePicture(
        // @Parameter(description = "ID of the provider", required = true)
        // @PathVariable Long providerId) throws IOException {
        // providerService.updateProfilePicture(providerId, null);
        // return ResponseEntity.noContent().build();
        // }

        // === SCHEDULE MANAGEMENT ===

        @PreAuthorize("hasAnyRole('PROVIDER', 'ADMIN', 'CONSUMER')")
        @GetMapping("/{providerId}/schedule")
        @Operation(summary = "Get provider's complete schedule with working days and time slots")
        @ApiResponses(value = {
                @ApiResponse(responseCode = "200", description = "Successfully retrieved schedule", content = @Content(schema = @Schema(implementation = ScheduleResponse.class))),
                @ApiResponse(responseCode = "404", description = "Provider not found")
        })
        public ResponseEntity<ScheduleResponse> getSchedule(
                @Parameter(description = "ID of the provider", required = true) @PathVariable Long providerId) {
                ScheduleResponse response = providerService.getSchedule(providerId);
                return ResponseEntity.ok(response);
        }

        @PreAuthorize("hasRole('PROVIDER')")
        @PostMapping("/me/schedule/working-days")
        @Operation(summary = "Add a working day template (recurring weekly schedule)")
        @ApiResponses(value = {
                @ApiResponse(responseCode = "201", description = "Working day added successfully", content = @Content(schema = @Schema(implementation = ScheduleResponse.class))),
                @ApiResponse(responseCode = "400", description = "Invalid working day data or duplicate day"),
                @ApiResponse(responseCode = "404", description = "Provider not found")
        })
        public ResponseEntity<ScheduleResponse> addWorkingDay(
                @AuthenticationPrincipal(expression = "user.id") Long providerId,
                @Parameter(description = "Working day data (day of week, start time, end time)", required = true) @Valid @RequestBody WorkingDayRequest request) {
                ScheduleResponse response = providerService.addWorkingDay(providerId, request);
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        @PreAuthorize("hasRole('PROVIDER')")
        @PutMapping("/me/schedule/working-days/{workingDayId}")
        @Operation(summary = "Update my existing working day template")
        @ApiResponses(value = {
                @ApiResponse(responseCode = "200", description = "Working day updated successfully", content = @Content(schema = @Schema(implementation = ScheduleResponse.class))),
                @ApiResponse(responseCode = "400", description = "Invalid working day data"),
                @ApiResponse(responseCode = "404", description = "Provider or working day not found")
        })
        public ResponseEntity<ScheduleResponse> updateWorkingDay(
                @Parameter(description = "ID of the provider", required = true) @AuthenticationPrincipal(expression = "user.id") Long providerId,
                @Parameter(description = "ID of the working day", required = true) @PathVariable Long workingDayId,
                @Parameter(description = "Updated working day data", required = true) @Valid @RequestBody WorkingDayRequest request) {
                ScheduleResponse response = providerService.updateWorkingDay(providerId, workingDayId, request);
                return ResponseEntity.ok(response);
        }

        @PreAuthorize("hasRole('PROVIDER')")
        @DeleteMapping("/me/schedule/working-days/{workingDayId}")
        @Operation(summary = "Remove a working day template and its future available slots")
        @ApiResponses(value = {
                @ApiResponse(responseCode = "200", description = "Working day removed successfully", content = @Content(schema = @Schema(implementation = ScheduleResponse.class))),
                @ApiResponse(responseCode = "404", description = "Provider or working day not found")
        })
        public ResponseEntity<ScheduleResponse> removeWorkingDay(
                @Parameter(description = "ID of the provider", required = true) @AuthenticationPrincipal(expression = "user.id") Long providerId,
                @Parameter(description = "ID of the working day to remove", required = true) @PathVariable Long workingDayId) {
                ScheduleResponse response = providerService.removeWorkingDay(providerId, workingDayId);
                return ResponseEntity.ok(response);
        }

        // ===== Time Slots Management =====

        @PreAuthorize("hasRole('PROVIDER')")
        @GetMapping("/me/time-slots")
        @Operation(summary = "Get my time slots for a specific date")
        @ApiResponses(value = {
                @ApiResponse(responseCode = "200", description = "Successfully retrieved time slots"),
                @ApiResponse(responseCode = "404", description = "Provider not found")
        })
        public ResponseEntity<List<ScheduleResponse.TimeSlotResponse>> getTimeSlotsByDate(
                @Parameter(description = "ID of the provider", required = true) @AuthenticationPrincipal(expression = "user.id") Long providerId,
                @Parameter(description = "Date (format: yyyy-MM-dd)", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
                List<ScheduleResponse.TimeSlotResponse> response = providerService.getTimeSlotsByDate(providerId, date);
                return ResponseEntity.ok(response);
        }

        @GetMapping("/{providerId}/available-slots")
        @PreAuthorize("hasRole('CONSUMER')")
        @Operation(summary = "Get ONLY available (not booked) time slots for a specific date")
        @ApiResponses(value = {
                @ApiResponse(responseCode = "200", description = "Successfully retrieved available time slots"),
                @ApiResponse(responseCode = "404", description = "Provider not found")
        })
        public ResponseEntity<List<ScheduleResponse.TimeSlotResponse>> getAvailableTimeSlots(
                @Parameter(description = "ID of the provider", required = true) @PathVariable Long providerId,
                @Parameter(description = "Date (format: yyyy-MM-dd)", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
                List<ScheduleResponse.TimeSlotResponse> response = providerService.getAvailableTimeSlots(providerId,
                        date);
                return ResponseEntity.ok(response);
        }

        @GetMapping("/me/available-slots")
        @PreAuthorize("hasRole('PROVIDER')")
        @Operation(summary = "Get ONLY available (not booked) time slots for a specific date")
        @ApiResponses(value = {
                @ApiResponse(responseCode = "200", description = "Successfully retrieved available time slots"),
                @ApiResponse(responseCode = "404", description = "Provider not found")
        })
        public ResponseEntity<List<ScheduleResponse.TimeSlotResponse>> getMyAvailableTimeSlots(
                @Parameter(description = "ID of the provider", required = true) @AuthenticationPrincipal(expression = "user.id") Long providerId,
                @Parameter(description = "Date (format: yyyy-MM-dd)", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
                List<ScheduleResponse.TimeSlotResponse> response = providerService.getAvailableTimeSlots(providerId,
                        date);
                return ResponseEntity.ok(response);
        }

        // @GetMapping("/{providerId}/time-slots/range")
        // @Operation(summary = "Get all time slots within a date range")
        // @ApiResponses(value = {
        // @ApiResponse(responseCode = "200", description = "Successfully retrieved time
        // slots"),
        // @ApiResponse(responseCode = "404", description = "Provider not found")
        // })
        // public ResponseEntity<List<ScheduleResponse.TimeSlotResponse>>
        // getTimeSlotsInRange(
        // @Parameter(description = "ID of the provider", required = true)
        // @PathVariable Long providerId,
        // @Parameter(description = "Start date (format: yyyy-MM-dd)", required = true)
        // @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate
        // startDate,
        // @Parameter(description = "End date (format: yyyy-MM-dd)", required = true,
        // example = "2026-03-07")
        // @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate
        // endDate) {
        // List<ScheduleResponse.TimeSlotResponse> response =
        // providerService.getTimeSlotsInRange(providerId, startDate, endDate);
        // return ResponseEntity.ok(response);
        // }

        @GetMapping("/me/time-slots/{timeSlotId}")
        @PreAuthorize("hasRole('PROVIDER')")
        @Operation(summary = "Get a specific time slot by ID")
        @ApiResponses(value = {
                @ApiResponse(responseCode = "200", description = "Successfully retrieved time slot"),
                @ApiResponse(responseCode = "404", description = "Time slot not found")
        })
        public ResponseEntity<ScheduleResponse.TimeSlotResponse> getTimeSlotById(
                @Parameter(description = "ID of the provider", required = true) @AuthenticationPrincipal(expression = "user.id") Long providerId,
                @Parameter(description = "ID of the time slot", required = true) @PathVariable Long timeSlotId) {
                ScheduleResponse.TimeSlotResponse response = providerService.getTimeSlot(providerId, timeSlotId);
                return ResponseEntity.ok(response);
        }

        // ===== Booking =====

        // @PreAuthorize("hasRole('PROVIDER')")
        // @PostMapping("/{providerId}/time-slots/book")
        // @Operation(summary = "Book a time slot by date/start time and duration")
        // @ApiResponses(value = {
        // @ApiResponse(responseCode = "200", description = "Time slot booked
        // successfully"),
        // @ApiResponse(responseCode = "400", description = "Time slot not available or
        // duration exceeds slot"),
        // @ApiResponse(responseCode = "404", description = "Provider or time slot not
        // found")
        // })
        // public ResponseEntity<ScheduleResponse.TimeSlotResponse>
        // acceptBookingRequest(
        // @Parameter(description = "ID of the provider", required = true) @PathVariable
        // Long providerId,
        // @Parameter(description = "Date (format: yyyy-MM-dd)", required = true)
        // @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        // @Parameter(description = "Start time (format: HH:mm)", required = true)
        // @RequestParam @DateTimeFormat(pattern = "HH:mm") LocalTime startTime,
        // @Parameter(description = "Duration in minutes", required = true)
        // @RequestParam Integer durationMinutes) {
        // ScheduleResponse.TimeSlotResponse response =
        // providerService.bookTimeSlot(providerId, date, startTime,
        // durationMinutes);
        // return ResponseEntity.ok(response);
        // }

        @PostMapping("/time-slots/{timeSlotId}/cancel-booking")
        @PreAuthorize("hasRole('PROVIDER')")
        @Operation(summary = "Cancel a booking and make the time slot available again")
        @ApiResponses(value = {
                @ApiResponse(responseCode = "200", description = "Booking cancelled successfully"),
                @ApiResponse(responseCode = "400", description = "Time slot is not booked"),
                @ApiResponse(responseCode = "404", description = "Time slot not found")
        })
        public ResponseEntity<ScheduleResponse.TimeSlotResponse> cancelBooking(
                @Parameter(description = "ID of the time slot", required = true) @PathVariable Long timeSlotId) {
                ScheduleResponse.TimeSlotResponse response = providerService.cancelBooking(timeSlotId);
                return ResponseEntity.ok(response);
        }

        // ===== Provider Schedule Overview =====

        @GetMapping("/me/schedule/upcoming")
        @PreAuthorize("hasRole('PROVIDER')")
        @Operation(summary = "Get upcoming available slots for the next N days")
        @ApiResponses(value = {
                @ApiResponse(responseCode = "200", description = "Successfully retrieved upcoming slots"),
                @ApiResponse(responseCode = "404", description = "Provider not found")
        })
        public ResponseEntity<List<ScheduleResponse.TimeSlotResponse>> getUpcomingAvailableSlots(
                @Parameter(description = "ID of the provider", required = true) @AuthenticationPrincipal(expression = "user.id") Long providerId,
                @Parameter(description = "Number of days to look ahead", required = true) @RequestParam(defaultValue = "7") Integer days) {
                List<ScheduleResponse.TimeSlotResponse> response = providerService.getUpcomingAvailableSlots(providerId,
                        days);
                return ResponseEntity.ok(response);
        }

        // }
        // ===== Document Management =====

        @PreAuthorize("hasRole('PROVIDER')")
        @PostMapping(value = "/me/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @Operation(summary = "Upload document")
        @ApiResponses(value = {
                @ApiResponse(responseCode = "201", description = "Successfully uploaded document", content = @Content(schema = @Schema(implementation = DocumentResponse.class))),
                @ApiResponse(responseCode = "404", description = "Provider not found"),
                @ApiResponse(responseCode = "400", description = "Invalid file format or size")
        })
        public ResponseEntity<DocumentResponse> uploadDocument(
                @Parameter(description = "ID of the provider", required = true) @AuthenticationPrincipal(expression = "user.id") Long providerId,

                @Parameter(description = "Document file (max 10MB, supported formats: PDF, DOC, DOCX, XLS, XLSX, TXT)", required = true) @RequestParam("file") MultipartFile file,

                @Parameter(description = "Document type (e.g., LICENSE, CERTIFICATE, ID)", required = true, example = "LICENSE") @RequestParam String documentType)
                throws IOException {
                DocumentResponse response = providerService.uploadDocument(providerId, file, documentType);
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        @PreAuthorize("hasAnyRole('ADMIN', 'CONSUMER', 'PROVIDER')")
        @GetMapping("/{providerId}/documents")
        @Operation(summary = "Get provider documents")
        @ApiResponses(value = {
                @ApiResponse(responseCode = "200", description = "Successfully retrieved documents", content = @Content(schema = @Schema(implementation = DocumentResponse.class))),
                @ApiResponse(responseCode = "404", description = "Provider not found")
        })
        public ResponseEntity<List<DocumentResponse>> getProviderDocuments(
                @Parameter(description = "ID of the provider", required = true) @PathVariable Long providerId) {
                List<DocumentResponse> response = providerService.getProviderDocuments(providerId);
                return ResponseEntity.ok(response);
        }

        @PreAuthorize("hasRole('PROVIDER')")
        @DeleteMapping("/me/documents/{documentId}")
        @Operation(summary = "Delete document")
        @ApiResponses(value = {
                @ApiResponse(responseCode = "200", description = "Successfully deleted document", content = @Content(schema = @Schema(implementation = ProfileResponse.class))),
                @ApiResponse(responseCode = "404", description = "Provider or document not found")
        })
        public ResponseEntity<ProfileResponse> deleteDocument(
                @Parameter(description = "ID of the provider", required = true) @AuthenticationPrincipal(expression = "user.id") Long providerId,
                @Parameter(description = "ID of the document", required = true) @PathVariable Long documentId) {
                ProfileResponse response = providerService.deleteDocument(providerId, documentId);
                return ResponseEntity.ok(response);
        }

        @GetMapping("/all")
        @Operation(summary = "all providers")
        @ApiResponses(value = {
                @ApiResponse(responseCode = "200", description = "Successfully found providers", content = @Content(schema = @Schema(implementation = ProviderSummaryResponse.class)))
        })
        public ResponseEntity<List<ProviderSummaryResponse>> allProviders() {
                List<ProviderSummaryResponse> response = providerService.allProviders();
                return ResponseEntity.ok(response);
        }

        // ===== Verification & Status =====

        @GetMapping("/{providerId}/verification-status")
        @Operation(summary = "Get verification status")
        @ApiResponses(value = {
                @ApiResponse(responseCode = "200", description = "Successfully retrieved status", content = @Content(schema = @Schema(implementation = ProviderResponse.class))),
                @ApiResponse(responseCode = "404", description = "Provider not found")
        })
        public ResponseEntity<VerificationStatus> getVerificationStatus(
                @Parameter(description = "ID of the provider", required = true) @PathVariable Long providerId) {
                VerificationStatus response = providerService.getVerificationStatus(providerId);
                return ResponseEntity.ok(response);
        }

        @PreAuthorize("hasRole('ADMIN')")
        @GetMapping("/{providerId}/exists")
        @Operation(summary = "Check if provider exists")
        @ApiResponses(value = {
                @ApiResponse(responseCode = "200", description = "Provider exists"),
                @ApiResponse(responseCode = "404", description = "Provider does not exist")
        })
        public ResponseEntity<Void> checkProviderExists(
                @Parameter(description = "ID of the provider", required = true) @PathVariable Long providerId) {
                try {
                        providerService.getProviderProfile(providerId);
                        return ResponseEntity.ok().build();
                } catch (Exception e) {
                        return ResponseEntity.notFound().build();
                }
        }

        @GetMapping("/search")
        @Operation(summary = "Search providers with filters and location-based sorting")
        @ApiResponses(value = {
                @ApiResponse(responseCode = "200", description = "Search completed successfully"),
                @ApiResponse(responseCode = "404", description = "Consumer not found")
        })
        public ResponseEntity<Page<SearchResponse>> search(
                @Parameter(description = "Search keyword (searches in name, bio, service type)") @RequestParam(required = false) String keyword,

                @Parameter(description = "Filter by category ID") @RequestParam(required = false) Long categoryId,

                @Parameter(description = "Filter by category name") @RequestParam(required = false) String categoryName,

                @Parameter(description = "Consumer ID for location-based search") @RequestParam(required = true) Long consumerId,

                @Parameter(description = "Search radius in kilometers (requires consumerId)") @RequestParam(required = false, defaultValue = "5.0") Double radius,

                @Parameter(description = "Sort by field (distance, rating, price, experience)") @RequestParam(required = false, defaultValue = "rating") String sortBy,

                @PageableDefault(size = 20, sort = "averageRating", direction = Sort.Direction.DESC) Pageable pageable) {
                Page<SearchResponse> page = providerService.search(
                        keyword, categoryId, categoryName, consumerId, radius, sortBy, pageable);

                return ResponseEntity.ok(page);
        }
        @GetMapping("/top-rated-near-me")
        @Operation(summary = "Get top 10 top-rated providers near the consumer based on location and scoring system")
        @PreAuthorize("hasRole('CONSUMER')")
        @ApiResponses(value = {
                @ApiResponse(responseCode = "200", description = "Top rated providers fetched successfully"),
                @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
                @ApiResponse(responseCode = "404", description = "Consumer not found"),
                @ApiResponse(responseCode = "500", description = "Internal server error")
        })

        public ResponseEntity<Page<SearchResponse>> topRatedNearMe(

                @Parameter(description = "Consumer ID for location-based search", required = true)
                @RequestParam Long consumerId,

                @Parameter(description = "Search radius in kilometers (default = 10 km)")
                @RequestParam(defaultValue = "10.0") Double radius,

                @Parameter(hidden = true)
                Pageable pageable
        ) {
                return ResponseEntity.ok(
                        providerService.topRatedNearMe(consumerId, radius, PageRequest.of(0, 10))
                );
        }
}