package com.aykhedma.mapper;

import com.aykhedma.dto.response.ScheduleResponse;
import com.aykhedma.model.booking.Schedule;
import com.aykhedma.model.booking.WorkingDay;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ScheduleMapper {

    @Mapping(target = "workingDays", source = "schedule", qualifiedByName = "mapWorkingDays")
    @Mapping(target = "availableSlots", ignore = true) // We'll handle this separately
    ScheduleResponse toScheduleResponse(Schedule schedule);

    @Named("mapWorkingDays")
    default List<ScheduleResponse.WorkingDayResponse> mapWorkingDays(Schedule schedule) {
        if (schedule == null || schedule.getWorkingDays() == null) {
            return List.of();
        }

        return schedule.getWorkingDays().stream()
                .map(this::toWorkingDayResponse)
                .collect(Collectors.toList());
    }

    default ScheduleResponse.WorkingDayResponse toWorkingDayResponse(WorkingDay workingDay) {
        if (workingDay == null) return null;

        return ScheduleResponse.WorkingDayResponse.builder()
                .id(workingDay.getId())
                .dayOfWeek(workingDay.getDayOfWeek().toString())
                .startTime(workingDay.getStartTime())
                .endTime(workingDay.getEndTime())
                .build();
    }
}