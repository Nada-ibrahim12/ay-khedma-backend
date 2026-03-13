package com.aykhedma.repository;

import com.aykhedma.model.booking.TimeSlot;
import com.aykhedma.model.booking.TimeSlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {
    List<TimeSlot> findByScheduleIdAndDateAndStatus(Long scheduleId, LocalDate date, TimeSlotStatus status);

    List<TimeSlot> findByScheduleIdAndDate(Long scheduleId, LocalDate date);

    List<TimeSlot> findByScheduleIdAndDateBetweenAndStatus(
                Long scheduleId, LocalDate startDate, LocalDate endDate, TimeSlotStatus status);

    boolean existsByScheduleIdAndDate(Long scheduleId, LocalDate date);

    boolean existsByScheduleIdAndDateAndStartTimeAndStatus(
            Long scheduleId, LocalDate date, LocalTime startTime, TimeSlotStatus status);

    @Query("SELECT t FROM TimeSlot t WHERE t.schedule.id = :scheduleId " +
            "AND t.date = :date " +
            "AND t.status = :status " +
            "AND ((t.startTime < :endTime AND t.endTime > :startTime))")
    List<TimeSlot> findConflictingSlots(@Param("scheduleId") Long scheduleId,
                                        @Param("date") LocalDate date,
                                        @Param("startTime") LocalTime startTime,
                                        @Param("endTime") LocalTime endTime,
                                        @Param("status") TimeSlotStatus status);
    @Modifying
    @Transactional
    @Query("DELETE FROM TimeSlot t WHERE t.schedule.id = :scheduleId " +
            "AND t.date BETWEEN :startDate AND :endDate " +
            "AND t.status = :status")
    void deleteByScheduleIdAndDateBetweenAndStatus(@Param("scheduleId") Long scheduleId,
                                                   @Param("startDate") LocalDate startDate,
                                                   @Param("endDate") LocalDate endDate,
                                                   @Param("status") TimeSlotStatus status);

    default List<TimeSlot> findAvailableSlots(Long scheduleId, LocalDate date) {
        return findByScheduleIdAndDateAndStatus(scheduleId, date, TimeSlotStatus.AVAILABLE);
    }

    default boolean isSlotAvailable(Long scheduleId, LocalDate date, LocalTime startTime) {
        return existsByScheduleIdAndDateAndStartTimeAndStatus(scheduleId, date, startTime, TimeSlotStatus.AVAILABLE);
    }

}