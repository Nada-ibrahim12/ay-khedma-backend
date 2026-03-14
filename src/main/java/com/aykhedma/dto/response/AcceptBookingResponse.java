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
public class AcceptBookingResponse
{
    private String status; // ACCEPTED or WARNING or CONFLICT
    private BookingResponse booking; // If accepted
    private List<BookingResponse> conflictingBookings; // If conflict
    private String warningMessage; // If warning for exceeding working hours
}
