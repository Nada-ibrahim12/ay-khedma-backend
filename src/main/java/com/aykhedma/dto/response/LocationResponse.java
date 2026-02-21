package com.aykhedma.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationResponse {
    private Long id;
    private Double latitude;
    private Double longitude;
    private String address;
    private String area;
    private String city;
    private String country;
    private String formattedAddress;
    private boolean success;
    private String message;
}