package com.aykhedma.controller;

import com.aykhedma.dto.request.RejectProviderRequest;
import com.aykhedma.dto.response.ProviderResponse;
import com.aykhedma.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

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
}
