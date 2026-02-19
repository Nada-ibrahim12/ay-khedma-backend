package com.aykhedma.repository;

import com.aykhedma.model.booking.WorkingDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.List;

@Repository
public interface WorkingDayRepository extends JpaRepository<WorkingDay, Long> {

    List<WorkingDay> findByScheduleId(Long scheduleId);

    @Modifying
    @Transactional
    @Query("DELETE FROM WorkingDay w WHERE w.schedule.id = :scheduleId AND w.dayOfWeek = :dayOfWeek")
    void deleteByScheduleIdAndDayOfWeek(@Param("scheduleId") Long scheduleId,
                                        @Param("dayOfWeek") DayOfWeek dayOfWeek);
}