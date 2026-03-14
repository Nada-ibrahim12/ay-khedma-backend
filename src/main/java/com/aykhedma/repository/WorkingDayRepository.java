package com.aykhedma.repository;

import com.aykhedma.model.booking.WorkingDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkingDayRepository extends JpaRepository<WorkingDay, Long> {

    // Find working day by schedule and specific date
    Optional<WorkingDay> findByScheduleIdAndDate(Long scheduleId, LocalDate date);

    // Find all working days for a schedule in date range
    List<WorkingDay> findByScheduleIdAndDateBetweenOrderByDateAsc(Long scheduleId, LocalDate startDate, LocalDate endDate);

    // Find all working days for a schedule after a certain date
    List<WorkingDay> findByScheduleIdAndDateAfterOrderByDateAsc(Long scheduleId, LocalDate date);

    // Check if a working day exists for a specific date
    boolean existsByScheduleIdAndDate(Long scheduleId, LocalDate date);

    // Delete working days before a certain date (cleanup old ones)
    @Modifying
    @Transactional
    @Query("DELETE FROM WorkingDay w WHERE w.schedule.id = :scheduleId AND w.date < :date")
    void deleteByScheduleIdAndDateBefore(@Param("scheduleId") Long scheduleId, @Param("date") LocalDate date);

    // Delete working day by schedule and date
    @Modifying
    @Transactional
    @Query("DELETE FROM WorkingDay w WHERE w.schedule.id = :scheduleId AND w.date = :date")
    void deleteByScheduleIdAndDate(@Param("scheduleId") Long scheduleId, @Param("date") LocalDate date);

    // Count working days in date range
    long countByScheduleIdAndDateBetween(Long scheduleId, LocalDate startDate, LocalDate endDate);
}