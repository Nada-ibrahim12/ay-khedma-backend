package com.aykhedma.mapper;

import com.aykhedma.dto.response.ScheduleResponse;
import com.aykhedma.model.booking.Schedule;
import com.aykhedma.model.booking.TimeSlot;
import com.aykhedma.model.booking.TimeSlotStatus;
import com.aykhedma.model.booking.WorkingDay;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ScheduleMapper {

    @Mapping(target = "workingDays", source = "schedule", qualifiedByName = "mapWorkingDays")
    @Mapping(target = "timeSlots", source = "schedule", qualifiedByName = "mapTimeSlots")
    ScheduleResponse toScheduleResponse(Schedule schedule);

    @Named("mapWorkingDays")
    default List<ScheduleResponse.WorkingDayResponse> mapWorkingDays(Schedule schedule) {
        if (schedule == null || schedule.getWorkingDays() == null) {
            return List.of();
        }

        return schedule.getWorkingDays().stream()
                .map(this::toWorkingDayResponse)
                .sorted(Comparator.comparing(wd -> LocalDate.parse(wd.getDate())))
                .collect(Collectors.toList());
    }

    default ScheduleResponse.WorkingDayResponse toWorkingDayResponse(WorkingDay workingDay) {
        if (workingDay == null) return null;

        return ScheduleResponse.WorkingDayResponse.builder()
                .id(workingDay.getId())
                .date(workingDay.getDate().toString())
                .startTime(workingDay.getStartTime())
                .endTime(workingDay.getEndTime())
                .build();
    }

    @Named("mapTimeSlots")
    default List<ScheduleResponse.TimeSlotResponse> mapTimeSlots(Schedule schedule) {
        if (schedule == null || schedule.getTimeSlots() == null) {
            return List.of();
        }

        return schedule.getTimeSlots().stream()
                .filter(slot -> slot.getStatus() == TimeSlotStatus.AVAILABLE) // Only show available slots
                .map(this::toTimeSlotResponse)
                .collect(Collectors.toList());
    }


    default ScheduleResponse.TimeSlotResponse toTimeSlotResponse(TimeSlot timeSlot) {
        if (timeSlot == null) return null;

        return ScheduleResponse.TimeSlotResponse.builder()
                .id(timeSlot.getId())
                .date(timeSlot.getDate().toString())
                .startTime(timeSlot.getStartTime())
                .endTime(timeSlot.getEndTime())
                .isBooked(timeSlot.getStatus() == TimeSlotStatus.BOOKED)
                .status(timeSlot.getStatus().name())
                .build();
    }
}