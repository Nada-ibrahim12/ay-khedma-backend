package com.aykhedma.dto.response;

import com.aykhedma.model.user.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminProviderResponse {

    private Long id;
    private String name;
    private String email;
    private String phoneNumber;
    private String profileImage;
    private boolean enabled;
    private VerificationStatus verificationStatus;
    private String rejectionReason;

    // Service info (from JOIN FETCH — no extra queries)
    private String serviceType;
    private String serviceTypeAr;
    private String serviceCategory;
    private String serviceCategoryAr;
    private Long serviceTypeId;

    // Key stats (scalar fields on Provider — no extra queries)
    private Integer completedJobs;
    private Integer totalBookings;
    private Double averageRating;
    private Integer acceptanceRate;
    private Double cancellationRate;

    // Location area (from JOIN FETCH — no extra queries)
    private String area;

    private LocalDateTime createdAt;
}
