package com.aykhedma.dto.response;

import com.aykhedma.model.user.UserType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerResponse {

    private Long id;
    private String name;
    private String email;
    private String phoneNumber;
    private String profileImage;
    private String preferredLanguage;
    private UserType role;
    private Double averageRating;
    private Integer totalBookings;
    private List<ProviderSummaryResponse> savedProviders;
}