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

    private List<String> workingDays;
    private LocalTime workStartTime;
    private LocalTime workEndTime;
    private List<BreakTimeResponse> breaks;
    private Integer slotDuration;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BreakTimeResponse {
        private LocalTime startTime;
        private LocalTime endTime;
        private String description;
    }
}