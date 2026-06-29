package com.aykhedma.dto.response;

import com.aykhedma.dto.location.LocationDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumerSummaryResponse {

    private Long id;
    private String name;
    private String profileImage;
    private String phoneNumber;
    private LocationResponse location;
}