package com.aykhedma.controller;

import com.aykhedma.dto.request.EmergencyRequestRequest;
import com.aykhedma.dto.request.PriceRecommendationRequest;
import com.aykhedma.dto.request.ProviderResponseRequest;
import com.aykhedma.dto.request.UpdateEmergencyRequestPriceRequest;
import com.aykhedma.dto.response.BookingResponse;
import com.aykhedma.dto.response.EmergencyRequestResponse;
import com.aykhedma.dto.response.PriceRecommendationResponse;
import com.aykhedma.dto.response.ProviderResponseResponse;
import com.aykhedma.service.EmergencyRequestService;
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
@RequestMapping("/api/emergency-requests")
@RequiredArgsConstructor
public class EmergencyRequestController
{
    private final EmergencyRequestService emergencyRequestService;

    // =================================== Consumer Side ===================================

    @PreAuthorize("hasRole('CONSUMER')")
    @GetMapping("/get-current-emergency-request")
    @Operation(summary = "Checks if there is a currently ongoing emergency request for this consumer")
    @ApiResponses(value =
            {
                   @ApiResponse(responseCode = "200", description = "Emergency request retrieved successfully",
                           content = @Content(schema = @Schema(implementation = EmergencyRequestResponse.class))),
                   @ApiResponse(responseCode = "404", description = "consumer or emergency request not found")
            })
    public ResponseEntity<EmergencyRequestResponse> getCurrentEmergencyRequest(
            @Parameter(description = "ID of the consumer", required = true)
            @AuthenticationPrincipal(expression = "user.id") Long consumerId)
    {
        EmergencyRequestResponse response = emergencyRequestService.getCurrentEmergencyRequest(consumerId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PreAuthorize("hasRole('CONSUMER')")
    @GetMapping("/get-emergency-request-price-recommendation")
    @Operation(summary = "Returns a price recommendation for an emergency request")
    @ApiResponses(value =
            {
                   @ApiResponse(responseCode = "200", description = "Price recommendation returned successfully",
                           content = @Content(schema = @Schema(implementation = EmergencyRequestResponse.class))),
                   @ApiResponse(responseCode = "204", description = "No price to be recommend"),
                   @ApiResponse(responseCode = "404", description = "Consumer or service type not found")
            })
    public ResponseEntity<PriceRecommendationResponse> getEmergencyRequestPriceRecommendation(
            @Parameter(description = "ID of the consumer", required = true)
            @AuthenticationPrincipal(expression = "user.id") Long consumerId,
            @Parameter(description = "Price Recommendation request data (service type ID, location)", required = true)
            @Valid @RequestBody PriceRecommendationRequest request)
    {
        PriceRecommendationResponse response = emergencyRequestService.getEmergencyRequestPriceRecommendation(consumerId, request);
        if (response.getPrice() == null)
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        else
            return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PreAuthorize("hasRole('CONSUMER')")
    @PostMapping("/request-emergency-request")
    @Operation(summary = "Request an emergency request")
    @ApiResponses(value =
            {
                   @ApiResponse(responseCode = "201", description = "Emergency request requested successfully",
                           content = @Content(schema = @Schema(implementation = EmergencyRequestResponse.class))),
                   @ApiResponse(responseCode = "400", description = "Invalid emergency request data"),
                   @ApiResponse(responseCode = "404", description = "Consumer or service type not found")
            })
    public ResponseEntity<EmergencyRequestResponse> requestEmergency(
            @Parameter(description = "ID of the consumer", required = true)
            @AuthenticationPrincipal(expression = "user.id") Long consumerId,
            @Parameter(description = "Emergency request request data (service type ID, location, suggested price, request description)", required = true)
            @Valid @RequestBody EmergencyRequestRequest request)
    {
        EmergencyRequestResponse response = emergencyRequestService.requestEmergencyRequest(consumerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PreAuthorize("hasRole('CONSUMER')")
    @PutMapping("/accept-provider-response/{providerResponseId}")
    @Operation(summary = "Accept a provider's response")
    @ApiResponses(value =
            {
                   @ApiResponse(responseCode = "200", description = "Provider response accepted successfully",
                           content = @Content(schema = @Schema(implementation = ProviderResponseResponse.class))),
                   @ApiResponse(responseCode = "400", description = "Invalid provider response to be accepted"),
                   @ApiResponse(responseCode = "403", description = "Consumer does not own the emergency request of this provider response"),
                   @ApiResponse(responseCode = "404", description = "consumer, response or emergency request not found")
            })
    public ResponseEntity<ProviderResponseResponse> acceptProviderResponse(
            @Parameter(description = "ID of the consumer", required = true)
            @AuthenticationPrincipal(expression = "user.id") Long consumerId,
            @Parameter(description = "ID of the provider response", required = true)
            @PathVariable Long providerResponseId)
    {
        ProviderResponseResponse response = emergencyRequestService.acceptProviderResponse(consumerId, providerResponseId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PreAuthorize("hasRole('CONSUMER')")
    @PutMapping("/decline-provider-response/{providerResponseId}")
    @Operation(summary = "Decline a provider's response")
    @ApiResponses(value =
            {
                   @ApiResponse(responseCode = "200", description = "Provider response declined successfully",
                           content = @Content(schema = @Schema(implementation = ProviderResponseResponse.class))),
                   @ApiResponse(responseCode = "400", description = "Invalid provider response to be declined"),
                   @ApiResponse(responseCode = "403", description = "Consumer does not own the emergency request of this provider response"),
                   @ApiResponse(responseCode = "404", description = "consumer, response or emergency request not found")
            })
    public ResponseEntity<ProviderResponseResponse> declineProviderResponse(
            @Parameter(description = "ID of the consumer", required = true)
            @AuthenticationPrincipal(expression = "user.id") Long consumerId,
            @Parameter(description = "ID of the provider response", required = true)
            @PathVariable Long providerResponseId)
    {
        ProviderResponseResponse response = emergencyRequestService.declineProviderResponse(consumerId, providerResponseId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PreAuthorize("hasRole('CONSUMER')")
    @PutMapping("/update-emergency-request-price")
    @Operation(summary = "Update an emergency request's suggested price")
    @ApiResponses(value =
            {
                   @ApiResponse(responseCode = "200", description = "Emergency request's price updated successfully",
                           content = @Content(schema = @Schema(implementation = EmergencyRequestResponse.class))),
                   @ApiResponse(responseCode = "400", description = "Invalid emergency request to be updated"),
                   @ApiResponse(responseCode = "403", description = "Consumer does not own the emergency request"),
                   @ApiResponse(responseCode = "404", description = "consumer or emergency request not found")
            })
    public ResponseEntity<EmergencyRequestResponse> updateEmergencyRequestPrice(
            @Parameter(description = "ID of the consumer", required = true)
            @AuthenticationPrincipal(expression = "user.id") Long consumerId,
            @Parameter(description = "Update emergency request price data (emergency request ID and updated price)", required = true)
            @Valid @RequestBody UpdateEmergencyRequestPriceRequest request)
    {
        EmergencyRequestResponse response = emergencyRequestService.updateEmergencyRequestPrice(consumerId, request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PreAuthorize("hasRole('CONSUMER')")
    @PutMapping("/complete-emergency-request/{emergencyRequestId}")
    @Operation(summary = "Complete an emergency request")
    @ApiResponses(value =
            {
                   @ApiResponse(responseCode = "200", description = "Emergency request completed successfully",
                           content = @Content(schema = @Schema(implementation = EmergencyRequestResponse.class))),
                   @ApiResponse(responseCode = "400", description = "Invalid emergency request to be completed"),
                   @ApiResponse(responseCode = "403", description = "Consumer does not own the emergency request"),
                   @ApiResponse(responseCode = "404", description = "consumer or emergency request not found")
            })
    public ResponseEntity<EmergencyRequestResponse> completeEmergencyRequest(
            @Parameter(description = "ID of the consumer", required = true)
            @AuthenticationPrincipal(expression = "user.id") Long consumerId,
            @Parameter(description = "ID of the emergency request", required = true)
            @PathVariable Long emergencyRequestId)
    {
        EmergencyRequestResponse response = emergencyRequestService.completeEmergencyRequest(consumerId, emergencyRequestId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PreAuthorize("hasRole('CONSUMER')")
    @PutMapping("/cancel-emergency-request/{emergencyRequestId}")
    @Operation(summary = "Cancel an emergency request")
    @ApiResponses(value =
            {
                   @ApiResponse(responseCode = "200", description = "Emergency request cancelled successfully",
                           content = @Content(schema = @Schema(implementation = EmergencyRequestResponse.class))),
                   @ApiResponse(responseCode = "400", description = "Invalid emergency request to be cancelled"),
                   @ApiResponse(responseCode = "403", description = "Consumer does not own the emergency request"),
                   @ApiResponse(responseCode = "404", description = "consumer or emergency request not found")
            })
    public ResponseEntity<EmergencyRequestResponse> cancelEmergencyRequest(
            @Parameter(description = "ID of the consumer", required = true)
            @AuthenticationPrincipal(expression = "user.id") Long consumerId,
            @Parameter(description = "ID of the emergency request", required = true)
            @PathVariable Long emergencyRequestId)
    {
        EmergencyRequestResponse response = emergencyRequestService.cancelEmergencyRequest(consumerId, emergencyRequestId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    // =================================== Provider Side ===================================

    @PreAuthorize("hasRole('PROVIDER')")
    @GetMapping("/get-pending-emergency-requests")
    @Operation(summary = "Get provider's pending emergency requests")
        @ApiResponses(value =
            {
                   @ApiResponse(responseCode = "200", description = "Emergency requests retrieved successfully",
                           content = @Content(schema = @Schema(implementation = ProviderResponseResponse.class))),
                   @ApiResponse(responseCode = "404", description = "Provider not found")
            })
    public ResponseEntity<List<ProviderResponseResponse>> getPendingEmergencyRequests(
            @Parameter(description = "ID of the provider", required = true)
            @AuthenticationPrincipal(expression = "user.id") Long providerId)
    {
        List<ProviderResponseResponse> response = emergencyRequestService.getPendingEmergencyRequests(providerId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PreAuthorize("hasRole('PROVIDER')")
    @PutMapping("/accept-emergency-request")
    @Operation(summary = "Accept a consumer's emergency request")
    @ApiResponses(value =
            {
                   @ApiResponse(responseCode = "200", description = "Emergency request accepted successfully",
                           content = @Content(schema = @Schema(implementation = ProviderResponseResponse.class))),
                   @ApiResponse(responseCode = "400", description = "Invalid emergency request to be accepted"),
                   @ApiResponse(responseCode = "403", description = "Provider does not own this provider response"),
                   @ApiResponse(responseCode = "404", description = "Provider, response or emergency request not found")
            })
    public ResponseEntity<ProviderResponseResponse> acceptEmergencyRequest(
            @Parameter(description = "ID of the provider", required = true)
            @AuthenticationPrincipal(expression = "user.id") Long providerId,
            @Parameter(description = "Accept emergency request request data (provider response ID, location, proposed price, notes)", required = true)
            @Valid @RequestBody ProviderResponseRequest request)
    {
        ProviderResponseResponse response = emergencyRequestService.acceptEmergencyRequest(providerId, request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PreAuthorize("hasRole('PROVIDER')")
    @PutMapping("/decline-emergency-request/{providerResponseId}")
    @Operation(summary = "Decline a consumer's emergency request")
    @ApiResponses(value =
            {
                   @ApiResponse(responseCode = "200", description = "Emergency request declined successfully",
                           content = @Content(schema = @Schema(implementation = ProviderResponseResponse.class))),
                   @ApiResponse(responseCode = "400", description = "Invalid emergency request to be declined"),
                   @ApiResponse(responseCode = "403", description = "Provider does not own this provider response"),
                   @ApiResponse(responseCode = "404", description = "Provider, response or emergency request not found")
            })
    public ResponseEntity<ProviderResponseResponse> declineEmergencyRequest(
            @Parameter(description = "ID of the provider", required = true)
            @AuthenticationPrincipal(expression = "user.id") Long providerId,
            @Parameter(description = "ID of the provider response", required = true)
            @PathVariable Long providerResponseId)
    {
        ProviderResponseResponse response = emergencyRequestService.declineEmergencyRequest(providerId, providerResponseId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    // =================================== User Side ===================================

    @PreAuthorize("hasAnyRole('PROVIDER','CONSUMER')")
    @GetMapping("/get-accepted-emergency-requests")
    @Operation(summary = "Get user's emergency requests history")
        @ApiResponses(value =
            {
                   @ApiResponse(responseCode = "200", description = "Emergency requests retrieved successfully",
                           content = @Content(schema = @Schema(implementation = BookingResponse.class))),
                   @ApiResponse(responseCode = "403", description = "User is not a provider or a consumer"),
                   @ApiResponse(responseCode = "404", description = "User not found")
            })
    public ResponseEntity<List<EmergencyRequestResponse>> getAcceptedEmergencyRequests(
            @Parameter(description = "ID of the user", required = true)
            @AuthenticationPrincipal(expression = "user.id") Long userId)
    {
        List<EmergencyRequestResponse> response = emergencyRequestService.getAcceptedEmergencyRequests(userId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PreAuthorize("hasAnyRole('PROVIDER','CONSUMER')")
    @GetMapping("/get-emergency-requests-history")
    @Operation(summary = "Get user's emergency requests history")
        @ApiResponses(value =
            {
                   @ApiResponse(responseCode = "200", description = "Emergency requests retrieved successfully",
                           content = @Content(schema = @Schema(implementation = BookingResponse.class))),
                   @ApiResponse(responseCode = "403", description = "User is not a provider or a consumer"),
                   @ApiResponse(responseCode = "404", description = "User not found")
            })
    public ResponseEntity<List<EmergencyRequestResponse>> getEmergencyRequestsHistory(
            @Parameter(description = "ID of the user", required = true)
            @AuthenticationPrincipal(expression = "user.id") Long userId)
    {
        List<EmergencyRequestResponse> response = emergencyRequestService.getEmergencyRequestsHistory(userId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
