package com.aykhedma.dto.response;

import com.aykhedma.dto.location.LocationDTO;
import com.aykhedma.model.service.PriceType;
import com.aykhedma.model.user.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderResponse {

    private Long id;
    private String name;
    private String email;
    private String phoneNumber;
    private String profileImage;
    private String bio;
    private Integer yearsOfExperience;
    private String nationalIdFrontImage;
    private String nationalIdBackImage;
    private String selfieImage;
    private VerificationStatus verificationStatus;
    private Integer completedJobs;
    private Integer totalBookings;
    private Integer cancelledBookings;
    private String serviceType;
    private String serviceTypeAr;
    private String serviceCategory;
    private String serviceCategoryAr;
    private Long serviceTypeId;
    private String worksAt;
    private String workLocation;
    private LocationResponse location;
    private Boolean emergencyEnabled;
    private Double averagePunctualityRating;
    private Double averageCommitmentRating;
    private Double averageQualityOfWorkRating;
    private Double averageRating;
    private Double averageJobs;
    private Double price;
    private PriceType priceType;
    private String priceTypeAr;
    private Integer averageTime;
    private Integer acceptanceRate;
    private Double cancellationRate;
    private Double averageInteractionRating;
    private Integer interactionRatingCount;
    private List<CancellationResponse> cancellationHistory;
    private List<DocumentResponse> documents;
    private ScheduleResponse schedule;
    private Double serviceAreaRadius;
    private String area;
    private String rejectionReason;
}