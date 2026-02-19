package com.aykhedma.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleResponse {
    private Long id;
    private List<WorkingDayResponse> workingDays;
    private List<TimeSlotResponse> availableSlots;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkingDayResponse {
        private Long id;
        private String dayOfWeek;
        private LocalTime startTime;
        private LocalTime endTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeSlotResponse {
        private Long id;
        private String date;
        private LocalTime startTime;
        private LocalTime endTime;
        private boolean isBooked;
    }
}