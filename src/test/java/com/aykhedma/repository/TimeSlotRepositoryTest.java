package com.aykhedma.repository;

import com.aykhedma.model.booking.Schedule;
import com.aykhedma.model.booking.TimeSlot;
import com.aykhedma.model.booking.TimeSlotStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("TimeSlotRepository Tests")
class TimeSlotRepositoryTest
{
    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Schedule schedule;
    private LocalDate date;

    @BeforeEach
    void setUp()
    {
        timeSlotRepository.deleteAll();
        entityManager.flush();

        schedule = Schedule.builder().build();
        entityManager.persist(schedule);

        date = LocalDate.now();
    }

    private TimeSlot buildTimeSlot(LocalTime start, LocalTime end, TimeSlotStatus status)
    {
        return TimeSlot.builder()
                .date(date)
                .startTime(start)
                .endTime(end)
                .status(status)
                .schedule(schedule)
                .build();
    }

    @Nested
    @DisplayName("Check If Time Is Within an Available Slot Tests")
    class IsTimeWithinAvailableSlotTest
    {
        @Test
        @DisplayName("Return true when time falls inside an available slot")
        void timeInsideAvailableSlotTest()
        {
            TimeSlot slot = buildTimeSlot
                    (LocalTime.of(10, 0), LocalTime.of(11, 0), TimeSlotStatus.AVAILABLE);
            timeSlotRepository.save(slot);

            boolean result = timeSlotRepository.isTimeWithinAvailableSlot
                    (schedule.getId(), date, LocalTime.of(10, 30));

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Return false when time does not fall inside an available slot")
        void timeOutsideAvailableSlotTest()
        {
            TimeSlot slot = buildTimeSlot
                    (LocalTime.of(10, 0), LocalTime.of(11, 0), TimeSlotStatus.AVAILABLE);
            timeSlotRepository.save(slot);

            boolean result = timeSlotRepository.isTimeWithinAvailableSlot
                    (schedule.getId(), date, LocalTime.of(11, 30));

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Return false when time falls only inside an unavailable slot")
        void slotNotAvailableTest()
        {
            TimeSlot slot = buildTimeSlot
                    (LocalTime.of(10, 0), LocalTime.of(11, 0), TimeSlotStatus.BOOKED);
           timeSlotRepository.save(slot);

            boolean result = timeSlotRepository.isTimeWithinAvailableSlot
                    (schedule.getId(), date, LocalTime.of(10, 30));

            assertThat(result).isFalse();
        }
    }
}
