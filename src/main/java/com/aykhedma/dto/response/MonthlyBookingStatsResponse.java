package com.aykhedma.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyBookingStatsResponse
{
    private List<String> months;
    private List<Long> completedBookings;
    private List<Long> cancelledBookings;

}
