package com.aykhedma.model.booking;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "schedules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotEmpty(message = "At least one working day must be selected")
    @ElementCollection
    @CollectionTable(name = "schedule_working_days", joinColumns = @JoinColumn(name = "schedule_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "working_day", length = 20)
    @Builder.Default
    private List<DayOfWeek> workingDays = new ArrayList<>();

    @NotNull(message = "Work start time is required")
    private LocalTime workStartTime;

    @NotNull(message = "Work end time is required")
    private LocalTime workEndTime;

    @AssertTrue(message = "Work end time must be after start time")
    private boolean isValidWorkHours() {
        if (workStartTime == null || workEndTime == null) return true;
        return workEndTime.isAfter(workStartTime);
    }

    @Min(value = 15, message = "Slot duration must be at least 15 minutes")
    @Max(value = 240, message = "Slot duration cannot exceed 4 hours (240 minutes)")
    @Column(nullable = false)
    private Integer slotDuration = 60;


}