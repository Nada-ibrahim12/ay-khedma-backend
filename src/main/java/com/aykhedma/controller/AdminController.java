package com.aykhedma.controller;

import com.aykhedma.auth.AuthService;
import com.aykhedma.dto.request.RegisterRequest;
import com.aykhedma.dto.request.RejectProviderRequest;
import com.aykhedma.dto.request.UpdateUserRequest;
import com.aykhedma.dto.response.AdminProviderResponse;
import com.aykhedma.dto.response.DashboardStatsResponse;
import com.aykhedma.dto.response.ProviderResponse;
import com.aykhedma.dto.response.UserResponse;
import com.aykhedma.exception.BadRequestException;
import com.aykhedma.model.user.UserType;
import com.aykhedma.model.user.VerificationStatus;
import com.aykhedma.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final AuthService authService;

    @GetMapping("/stats")
    @Operation(summary = "Get dashboard statistics", description = "Retrieve total users, providers, pending providers, and services")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully", content = @Content(schema = @Schema(implementation = DashboardStatsResponse.class))),
            @ApiResponse(responseCode = "403", description = "Unauthorized - admin access required")
    })
    public ResponseEntity<DashboardStatsResponse> getDashboardStats() {
        return ResponseEntity.ok(adminService.getDashboardStats());
    }

    @GetMapping("/providers/pending")
    @Operation(summary = "Get pending providers", description = "Retrieve list of providers awaiting verification")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of pending providers retrieved successfully", content = @Content(schema = @Schema(implementation = ProviderResponse.class))),
            @ApiResponse(responseCode = "403", description = "Unauthorized - admin access required")
    })
    public ResponseEntity<List<ProviderResponse>> getPendingProviders() {
        return ResponseEntity.ok(adminService.getPendingProviders());
    }

    @GetMapping("/providers/{id}")
    @Operation(summary = "Get provider details", description = "Retrieve detailed information about a specific provider")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Provider details retrieved successfully", content = @Content(schema = @Schema(implementation = ProviderResponse.class))),
            @ApiResponse(responseCode = "403", description = "Unauthorized - admin access required"),
            @ApiResponse(responseCode = "404", description = "Provider not found")
    })
    public ResponseEntity<ProviderResponse> getProviderDetails(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getProviderDetails(id));
    }

    @PutMapping("/providers/{id}/approve")
    @Operation(summary = "Approve provider", description = "Approve a provider's verification and enable their account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Provider approved successfully", content = @Content(schema = @Schema(implementation = ProviderResponse.class))),
            @ApiResponse(responseCode = "403", description = "Unauthorized - admin access required"),
            @ApiResponse(responseCode = "404", description = "Provider not found"),
            @ApiResponse(responseCode = "409", description = "Provider already approved")
    })
    public ResponseEntity<ProviderResponse> approveProvider(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.approveProvider(id));
    }

    @PutMapping("/providers/{id}/reject")
    @Operation(summary = "Reject provider", description = "Reject a provider's verification with reason")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Provider rejected successfully", content = @Content(schema = @Schema(implementation = ProviderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid rejection reason or request"),
            @ApiResponse(responseCode = "403", description = "Unauthorized - admin access required"),
            @ApiResponse(responseCode = "404", description = "Provider not found")
    })
    public ResponseEntity<ProviderResponse> rejectProvider(
            @PathVariable Long id,
            @RequestBody(required = false) RejectProviderRequest request,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(adminService.rejectProvider(id, validateRejectionReason(request, reason)));
    }

    @GetMapping("/providers")
    @Operation(summary = "Search providers", description = "Search and filter providers by keyword, status, and enabled state")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Providers found successfully", content = @Content(schema = @Schema(implementation = ProviderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid filter parameters"),
            @ApiResponse(responseCode = "403", description = "Unauthorized - admin access required")
    })
    public ResponseEntity<Page<AdminProviderResponse>> searchProviders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String verificationStatus,
            @RequestParam(required = false) String enabled,
            Pageable pageable) {
        return ResponseEntity.ok(adminService.searchProviders(
                firstPresent(keyword, search, q),
                parseVerificationStatus(firstPresent(status, verificationStatus)),
                parseEnabled(enabled),
                pageable));
    }

    @PutMapping("/providers/{id}/block")
    @Operation(summary = "Block provider", description = "Suspend a provider account temporarily")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Provider blocked successfully"),
            @ApiResponse(responseCode = "403", description = "Unauthorized - admin access required"),
            @ApiResponse(responseCode = "404", description = "Provider not found")
    })
    public ResponseEntity<Void> blockProvider(@PathVariable Long id) {
        adminService.suspendUser(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/providers/{id}/unblock")
    @Operation(summary = "Unblock provider", description = "Reactivate a suspended provider account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Provider unblocked successfully"),
            @ApiResponse(responseCode = "403", description = "Unauthorized - admin access required"),
            @ApiResponse(responseCode = "404", description = "Provider not found")
    })
    public ResponseEntity<Void> unblockProvider(@PathVariable Long id) {
        adminService.reactivateUser(id);
        return ResponseEntity.noContent().build();
    }

    // --- User Management Endpoints ---

    @GetMapping("/users")
    @Operation(summary = "Search users", description = "Search and filter users by role, status, and date range")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Users found successfully", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid filter parameters or date format"),
            @ApiResponse(responseCode = "403", description = "Unauthorized - admin access required")
    })
    public ResponseEntity<Page<UserResponse>> searchUsers(
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "enabled", required = false) String enabled,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "q", required = false) String q,
            @AuthenticationPrincipal(expression = "user.id") Long currentAdminId,
            Pageable pageable) {
        return ResponseEntity.ok(adminService.searchUsers(
                parseUserType(role),
                parseEnabled(firstPresent(enabled, status)),
                startDate,
                endDate,
                firstPresent(keyword, search, q),
                currentAdminId,
                pageable));
    }

    @PostMapping("/users")
    @Operation(summary = "Add new user", description = "Create a new user account with specified role and details")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request data or validation error"),
            @ApiResponse(responseCode = "403", description = "Unauthorized - admin access required"),
            @ApiResponse(responseCode = "409", description = "Conflict - email or phone already exists")
    })
    public ResponseEntity<String> addUser(@Valid @RequestBody RegisterRequest request) {
        authService.register(request, null, null, null, null, null);
        return ResponseEntity.ok("User created successfully");
    }

    @PutMapping("/users/{id}")
    @Operation(summary = "Update user", description = "Update user information and details")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User updated successfully", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request data or validation error"),
            @ApiResponse(responseCode = "403", description = "Unauthorized - admin access required"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(adminService.updateUser(id, request));
    }

    @PutMapping("/users/{id}/suspend")
    @Operation(summary = "Suspend user", description = "Temporarily suspend a user account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User suspended successfully", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "403", description = "Unauthorized - admin access required"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<UserResponse> suspendUser(@PathVariable("id") Long id) {
        return ResponseEntity.ok(adminService.suspendUser(id));
    }

    @PutMapping("/users/{id}/reactivate")
    @Operation(summary = "Reactivate user", description = "Reactivate a suspended user account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User reactivated successfully", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "403", description = "Unauthorized - admin access required"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<UserResponse> reactivateUser(@PathVariable("id") Long id) {
        return ResponseEntity.ok(adminService.reactivateUser(id));
    }

    @DeleteMapping("/users/{id}")
    @Operation(summary = "Delete user", description = "Permanently delete a user account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Unauthorized - admin access required"),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    public ResponseEntity<String> deleteUser(@PathVariable("id") Long id) {
        adminService.deleteUser(id);
        return ResponseEntity.ok("User deleted successfully");
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private UserType parseUserType(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UserType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid role filter: " + value);
        }
    }

    private VerificationStatus parseVerificationStatus(String value) {
        if (value == null) {
            return null;
        }
        try {
            return VerificationStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid provider status filter: " + value);
        }
    }

    private Boolean parseEnabled(String value) {
        if (value == null) {
            return null;
        }

        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "enabled", "active", "1", "yes" -> true;
            case "false", "disabled", "inactive", "suspended", "blocked", "0", "no" -> false;
            default -> throw new BadRequestException("Invalid enabled/status filter: " + value);
        };
    }

    private String validateRejectionReason(RejectProviderRequest request, String reasonParam) {
        String reason = firstPresent(request != null ? request.getReason() : null, reasonParam);
        if (reason == null) {
            throw new BadRequestException("Rejection reason is required");
        }
        if (reason.length() < 10 || reason.length() > 500) {
            throw new BadRequestException("Reason must be between 10 and 500 characters");
        }
        return reason;
    }
}
