package com.aykhedma.repository;

import com.aykhedma.model.booking.TimeSlot;
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

    List<TimeSlot> findByScheduleIdAndDateAndIsBookedFalse(Long scheduleId, LocalDate date);

    List<TimeSlot> findByScheduleIdAndDateAndIsBookedTrue(Long scheduleId, LocalDate date);

    @Query("SELECT t FROM TimeSlot t WHERE t.schedule.id = :scheduleId " +
            "AND t.date = :date " +
            "AND ((t.startTime < :endTime AND t.endTime > :startTime))")
    List<TimeSlot> findConflictingSlots(@Param("scheduleId") Long scheduleId,
                                        @Param("date") LocalDate date,
                                        @Param("startTime") LocalTime startTime,
                                        @Param("endTime") LocalTime endTime);

    @Modifying
    @Transactional
    @Query("DELETE FROM TimeSlot t WHERE t.schedule.id = :scheduleId " +
            "AND t.date BETWEEN :startDate AND :endDate")
    void deleteByScheduleIdAndDateBetween(@Param("scheduleId") Long scheduleId,
                                          @Param("startDate") LocalDate startDate,
                                          @Param("endDate") LocalDate endDate);

    boolean existsByScheduleIdAndDateAndStartTimeAndIsBookedFalse(
            Long scheduleId, LocalDate date, LocalTime startTime);
}