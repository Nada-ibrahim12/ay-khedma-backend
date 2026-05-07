package com.aykhedma.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyScheduleResponse {
    private List<WorkingDayWithSlots> workingDays;
    private LocalDate weekStartDate; // Saturday
    private LocalDate weekEndDate; // Friday

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkingDayWithSlots {
        private Long workingDayId;
        private LocalDate date;
        private LocalTime startTime;
        private LocalTime endTime;
        private String dayOfWeek; // "Saturday", "Sunday", etc.
        private List<TimeSlot> timeSlots;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSlot {
        private Long id;
        private LocalTime startTime;
        private LocalTime endTime;
        private String status; // AVAILABLE, BOOKED, CANCELLED
        private boolean isBooked;
    }
}
