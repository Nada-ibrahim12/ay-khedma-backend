package com.aykhedma.controller;

import com.aykhedma.auth.AuthService;
import com.aykhedma.dto.request.RegisterRequest;
import com.aykhedma.dto.request.RejectProviderRequest;
import com.aykhedma.dto.request.UpdateUserRequest;
import com.aykhedma.dto.response.ProviderResponse;
import com.aykhedma.dto.response.UserResponse;
import com.aykhedma.model.user.UserType;
import com.aykhedma.model.user.VerificationStatus;
import com.aykhedma.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final AuthService authService;

    @GetMapping("/providers/pending")
    public ResponseEntity<List<ProviderResponse>> getPendingProviders() {
        return ResponseEntity.ok(adminService.getPendingProviders());
    }

    @GetMapping("/providers/{id}")
    public ResponseEntity<ProviderResponse> getProviderDetails(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getProviderDetails(id));
    }

    @PutMapping("/providers/{id}/approve")
    public ResponseEntity<ProviderResponse> approveProvider(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.approveProvider(id));
    }

    @PutMapping("/providers/{id}/reject")
    public ResponseEntity<ProviderResponse> rejectProvider(
            @PathVariable Long id, 
            @Valid @RequestBody RejectProviderRequest request) {
        return ResponseEntity.ok(adminService.rejectProvider(id, request.getReason()));
    }

    @GetMapping("/providers")
    public ResponseEntity<Page<ProviderResponse>> searchProviders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) VerificationStatus status,
            @RequestParam(required = false) Boolean enabled,
            Pageable pageable) {
        return ResponseEntity.ok(adminService.searchProviders(keyword, status, enabled, pageable));
    }

    @PutMapping("/providers/{id}/block")
    public ResponseEntity<Void> blockProvider(@PathVariable Long id) {
        adminService.suspendUser(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/providers/{id}/unblock")
    public ResponseEntity<Void> unblockProvider(@PathVariable Long id) {
        adminService.reactivateUser(id);
        return ResponseEntity.noContent().build();
    }

    // --- User Management Endpoints ---

    @GetMapping("/users")
    public ResponseEntity<Page<UserResponse>> searchUsers(
            @RequestParam(value = "role",required = false) UserType role,
            @RequestParam(value = "status",required = false) Boolean status,
            @RequestParam(value = "startDate",required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(value = "endDate",required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            Pageable pageable) {
        return ResponseEntity.ok(adminService.searchUsers(role, status, startDate, endDate, pageable));
    }

    @PostMapping("/users")
    public ResponseEntity<String> addUser(@Valid @RequestBody RegisterRequest request) {
        authService.register(request, null, null, null);
        return ResponseEntity.ok("User created successfully");
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable("id") Long id, 
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(adminService.updateUser(id, request));
    }

    @PutMapping("/users/{id}/suspend")
    public ResponseEntity<UserResponse> suspendUser(@PathVariable("id") Long id) {
        return ResponseEntity.ok(adminService.suspendUser(id));
    }

    @PutMapping("/users/{id}/reactivate")
    public ResponseEntity<UserResponse> reactivateUser(@PathVariable("id") Long id) {
        return ResponseEntity.ok(adminService.reactivateUser(id));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable("id") Long id) {
        adminService.deleteUser(id);
        return ResponseEntity.ok("User deleted successfully");
    }
}
