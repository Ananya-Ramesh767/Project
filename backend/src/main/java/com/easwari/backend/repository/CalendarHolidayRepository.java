package com.easwari.backend.repository;

import com.easwari.backend.domain.CalendarHoliday;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface CalendarHolidayRepository extends JpaRepository<CalendarHoliday, Long> {
    List<CalendarHoliday> findByCalendarId(Long calendarId);
    boolean existsByHolidayDateAndName(LocalDate holidayDate, String name);
    List<CalendarHoliday> findByHolidayDateBetween(LocalDate start, LocalDate end);
}

