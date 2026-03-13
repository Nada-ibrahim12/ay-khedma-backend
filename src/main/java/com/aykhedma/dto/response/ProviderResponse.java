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
    private VerificationStatus verificationStatus;
    private Integer completedJobs;
    private String serviceType;
    private Long serviceTypeId;
    private LocationDTO location;
    private Boolean emergencyEnabled;
    private Double averageRating;
    private Double price;
    private PriceType priceType;
    private Integer averageTime;
    private Integer acceptanceRate;
    private List<DocumentResponse> documents;
    private ScheduleResponse schedule;
    private Double serviceAreaRadius;
    private String serviceArea;
}