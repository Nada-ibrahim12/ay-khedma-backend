package com.aykhedma.controller;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.dto.response.DistanceResponse;
import com.aykhedma.dto.response.LocationResponse;
import com.aykhedma.dto.response.ProviderSummaryResponse;
import com.aykhedma.service.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/locations")
@RequiredArgsConstructor
public class LocationController {

        private final LocationService locationService;

        // ======= CONSUMER LOCATION ENDPOINTS =======

        @PreAuthorize("hasRole('CONSUMER')")
        @PostMapping("/consumer/me")
        @Operation(summary = "Save my location")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "201", description = "Location saved successfully", content = @Content(schema = @Schema(implementation = LocationResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Consumer not found"),
                        @ApiResponse(responseCode = "400", description = "Invalid location data"),
                        @ApiResponse(responseCode = "409", description = "Consumer already has a location")
        })
        public ResponseEntity<LocationResponse> saveMyConsumerLocation(
                        @AuthenticationPrincipal(expression = "user.id") Long consumerId,
                        @Valid @RequestBody LocationDTO locationDTO) {
                LocationResponse response = locationService.saveConsumerLocation(consumerId, locationDTO);
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        @PreAuthorize("hasRole('CONSUMER')")
        @PutMapping("/consumer/me")
        @Operation(summary = "Update my location")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Location updated successfully", content = @Content(schema = @Schema(implementation = LocationResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Consumer or location not found"),
                        @ApiResponse(responseCode = "400", description = "Invalid location data")
        })
        public ResponseEntity<LocationResponse> updateMyConsumerLocation(
                        @AuthenticationPrincipal(expression = "user.id") Long consumerId,
                        @Valid @RequestBody LocationDTO locationDTO) {
                LocationResponse response = locationService.updateConsumerLocation(consumerId, locationDTO);
                return ResponseEntity.ok(response);
        }

        @PreAuthorize("hasRole('CONSUMER')")
        @PatchMapping("/consumers/me")
        @Operation(summary = "Patch my location")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Location patched successfully", content = @Content(schema = @Schema(implementation = LocationResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Consumer or location not found"),
                        @ApiResponse(responseCode = "400", description = "Invalid location data")
        })
        public ResponseEntity<LocationResponse> patchMyConsumerLocation(
                        @AuthenticationPrincipal(expression = "user.id") Long consumerId,
                        @RequestBody LocationDTO locationDTO) {
                LocationResponse response = locationService.patchConsumerLocation(consumerId, locationDTO);
                return ResponseEntity.ok(response);
        }

        // GET CONSUMER LOCATION -> ADMIN/PROVIDER
        @PreAuthorize("hasAnyRole('ADMIN', 'PROVIDER')")
        @GetMapping("/consumer/{consumerId}")
        @Operation(summary = "Get consumer location")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Location retrieved successfully", content = @Content(schema = @Schema(implementation = LocationDTO.class))),
                        @ApiResponse(responseCode = "404", description = "Consumer or location not found")
        })
        public ResponseEntity<LocationDTO> getConsumerLocation(
                        @AuthenticationPrincipal(expression = "user.id") Long consumerId) {
                LocationDTO response = locationService.getConsumerLocation(consumerId);
                return ResponseEntity.ok(response);
        }

        @PreAuthorize("hasRole('CONSUMER')")
        @GetMapping("/consumers/me")
        @Operation(summary = "Get my location")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Location retrieved successfully", content = @Content(schema = @Schema(implementation = LocationDTO.class))),
                        @ApiResponse(responseCode = "404", description = "Consumer or location not found")
        })
        public ResponseEntity<LocationDTO> getMyConsumerLocation(
                        @AuthenticationPrincipal(expression = "user.id") Long consumerId) {
                LocationDTO response = locationService.getConsumerLocation(consumerId);
                return ResponseEntity.ok(response);
        }

        // @DeleteMapping("/consumers/{consumerId}")
        // @Operation(summary = "Delete consumer location")
        // @ApiResponses(value = {
        // @ApiResponse(responseCode = "200", description = "Location deleted
        // successfully",
        // content = @Content(schema = @Schema(implementation =
        // LocationResponse.class))),
        // @ApiResponse(responseCode = "404", description = "Consumer not found")
        // })
        // public ResponseEntity<LocationResponse> deleteConsumerLocation(
        // @Parameter(description = "Consumer ID", required = true)
        // @PathVariable Long consumerId) {
        // LocationResponse response =
        // locationService.deleteConsumerLocation(consumerId);
        // return ResponseEntity.ok(response);
        // }

        // ======= PROVIDER LOCATION ENDPOINTS =======

        @PreAuthorize("hasRole('PROVIDER')")
        @PostMapping("/provider/me")
        @Operation(summary = "Save my location")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "201", description = "Location saved successfully", content = @Content(schema = @Schema(implementation = LocationResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Provider not found"),
                        @ApiResponse(responseCode = "400", description = "Invalid location data"),
                        @ApiResponse(responseCode = "409", description = "Provider already has a location")
        })
        public ResponseEntity<LocationResponse> saveMyProviderLocation(
                        @AuthenticationPrincipal(expression = "user.id") Long providerId,
                        @Valid @RequestBody LocationDTO locationDTO) {
                LocationResponse response = locationService.saveProviderLocation(providerId, locationDTO);
                return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        @PreAuthorize("hasRole('PROVIDER')")
        @PutMapping("/providers/me")
        @Operation(summary = "Update my location")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Location updated successfully", content = @Content(schema = @Schema(implementation = LocationResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Provider or location not found"),
                        @ApiResponse(responseCode = "400", description = "Invalid location data")
        })
        public ResponseEntity<LocationResponse> updateMyProviderLocation(
                        @AuthenticationPrincipal(expression = "user.id") Long providerId,
                        @Valid @RequestBody LocationDTO locationDTO) {
                LocationResponse response = locationService.updateProviderLocation(providerId, locationDTO);
                return ResponseEntity.ok(response);
        }

        @PreAuthorize("hasRole('PROVIDER')")
        @PatchMapping("/provider/me")
        @Operation(summary = "Patch my location")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Location patched successfully", content = @Content(schema = @Schema(implementation = LocationResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Provider or location not found"),
                        @ApiResponse(responseCode = "400", description = "Invalid location data")
        })
        public ResponseEntity<LocationResponse> patchMyProviderLocation(
                        @AuthenticationPrincipal(expression = "user.id") Long providerId,
                        @RequestBody LocationDTO locationDTO) {
                LocationResponse response = locationService.patchProviderLocation(providerId, locationDTO);
                return ResponseEntity.ok(response);
        }

        @PreAuthorize("hasAnyRole('CONSUMER', 'ADMIN')")
        @GetMapping("/providers/{providerId}")
        @Operation(summary = "Get provider location")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Location retrieved successfully", content = @Content(schema = @Schema(implementation = LocationDTO.class))),
                        @ApiResponse(responseCode = "404", description = "Provider or location not found")
        })
        public ResponseEntity<LocationDTO> getProviderLocation(
                        @Parameter(description = "Provider ID", required = true) @PathVariable Long providerId) {
                LocationDTO response = locationService.getProviderLocation(providerId);
                return ResponseEntity.ok(response);
        }

        @PreAuthorize("hasRole('PROVIDER')")
        @GetMapping("/providers/me")
        @Operation(summary = "Get my location")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Location retrieved successfully", content = @Content(schema = @Schema(implementation = LocationDTO.class))),
                        @ApiResponse(responseCode = "404", description = "Provider or location not found")
        })
        public ResponseEntity<LocationDTO> getMyProviderLocation(
                        @AuthenticationPrincipal(expression = "user.id") Long providerId) {
                LocationDTO response = locationService.getProviderLocation(providerId);
                return ResponseEntity.ok(response);
        }

        // @DeleteMapping("/providers/{providerId}")
        // @Operation(summary = "Delete provider location")
        // @ApiResponses(value = {
        // @ApiResponse(responseCode = "200", description = "Location deleted
        // successfully",
        // content = @Content(schema = @Schema(implementation =
        // LocationResponse.class))),
        // @ApiResponse(responseCode = "404", description = "Provider not found")
        // })
        // public ResponseEntity<LocationResponse> deleteProviderLocation(
        // @Parameter(description = "Provider ID", required = true)
        // @PathVariable Long providerId) {
        // LocationResponse response =
        // locationService.deleteProviderLocation(providerId);
        // return ResponseEntity.ok(response);
        // }

        // ======= DISTANCE CALCULATION ENDPOINTS =======

        @GetMapping("/distance/consumer/{consumerId}/provider/{providerId}")
        @Operation(summary = "Calculate distance between consumer and provider", description = "Calculate the distance between a consumer and a provider in kilometers")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Distance calculated successfully", content = @Content(schema = @Schema(implementation = DistanceResponse.class))),
                        @ApiResponse(responseCode = "404", description = "Consumer, provider, or their locations not found")
        })
        public ResponseEntity<DistanceResponse> calculateDistanceBetweenConsumerAndProvider(
                        @Parameter(description = "Consumer ID", required = true) @PathVariable Long consumerId,

                        @Parameter(description = "Provider ID", required = true) @PathVariable Long providerId) {
                DistanceResponse response = locationService.calculateDistanceBetweenConsumerAndProvider(consumerId,
                                providerId);
                return ResponseEntity.ok(response);
        }

        @GetMapping("/provider/{providerId}/service-area-check")
        @Operation(summary = "Check if location is within provider's service area", description = "Check if a specific location is within a provider's service area")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Check completed successfully"),
                        @ApiResponse(responseCode = "404", description = "Provider or location not found")
        })
        public ResponseEntity<Boolean> isLocationWithinServiceArea(
                        @Parameter(description = "Provider ID", required = true) @PathVariable Long providerId,

                        @Parameter(description = "Latitude", required = true) @RequestParam Double latitude,

                        @Parameter(description = "Longitude", required = true) @RequestParam Double longitude) {
                Boolean isWithin = locationService.isLocationWithinServiceArea(providerId, latitude, longitude);
                return ResponseEntity.ok(isWithin);
        }

        @GetMapping("/provider/{providerId}/service-area/consumer/{consumerId}")
        @Operation(summary = "Check if consumer is within provider's service area", description = "Check if a consumer's location is within a provider's service area")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Check completed successfully"),
                        @ApiResponse(responseCode = "404", description = "Provider, consumer, or their locations not found")
        })
        public ResponseEntity<Boolean> isConsumerWithinProviderServiceArea(
                        @Parameter(description = "Provider ID", required = true) @PathVariable Long providerId,

                        @Parameter(description = "Consumer ID", required = true) @PathVariable Long consumerId) {

                // First get consumer location
                LocationDTO consumerLocation = locationService.getConsumerLocation(consumerId);

                // Check if within service area
                Boolean isWithin = locationService.isLocationWithinServiceArea(
                                providerId,
                                consumerLocation.getLatitude(),
                                consumerLocation.getLongitude());
                return ResponseEntity.ok(isWithin);
        }

        // ======= BULK OPERATIONS =======

        @GetMapping("/providers/nearby")
        @Operation(summary = "Find providers near a location", description = "Find all providers within a specified radius of a point")
        @ApiResponses(value = {
                        @ApiResponse(responseCode = "200", description = "Search completed successfully")
        })
        public ResponseEntity<List<ProviderSummaryResponse>> findNearbyProviders(
                        @Parameter(description = "Latitude", required = true) @RequestParam Double latitude,

                        @Parameter(description = "Longitude", required = true) @RequestParam Double longitude,

                        @Parameter(description = "Radius in kilometers", required = true) @RequestParam Double radius,

                        @Parameter(description = "Service type ID (optional)") @RequestParam(required = false) Long serviceTypeId) {

                // You'll need to implement this method in your service
                // This would query providers and filter by distance
                List<ProviderSummaryResponse> providers = locationService.findNearbyProviders(
                                latitude, longitude, radius, serviceTypeId);
                return ResponseEntity.ok(providers);
        }
}