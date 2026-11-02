package com.easwari.backend.service;

import com.easwari.backend.domain.AcademicCalendar;
import com.easwari.backend.domain.CalendarHoliday;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implements LESSON_PLAN_CONTRACT.md section 2 (working-day calculation algorithm).
 * Sole source of truth for which calendar dates are teaching days: an
 * AcademicCalendar's startDate/endDate, SaturdayRule/SundayRule, and its
 * CalendarHoliday rows. Never a hardcoded holiday list. Reusable by any future
 * consumer (Person B's scheduler included) per Person A doc section 26.
 */
@Service
public class WorkingDayCalendarService {

    public List<LocalDate> computeTeachingDates(AcademicCalendar calendar) {
        Set<LocalDate> holidaySet = new HashSet<>();
        if (calendar.getHolidays() != null) {
            for (CalendarHoliday h : calendar.getHolidays()) {
                holidaySet.add(h.getHolidayDate());
            }
        }

        List<LocalDate> teachingDates = new ArrayList<>();
        LocalDate date = calendar.getStartDate();
        LocalDate end = calendar.getEndDate();

        while (date != null && end != null && !date.isAfter(end)) {
            DayOfWeek dow = date.getDayOfWeek();
            boolean nonTeaching = false;

            if (dow == DayOfWeek.SATURDAY) {
                nonTeaching = resolveSaturdayRule(calendar.getSaturdayRule(), date);
            } else if (dow == DayOfWeek.SUNDAY) {
                nonTeaching = resolveSundayRule(calendar.getSundayRule(), date);
            }
            // Monday..Friday: teaching by default (Academic Regulations 2025, Clause 9:
            // "Monday-Friday only for regular classes" — Saturday/Sunday are the only
            // days whose status depends on a configurable rule.

            if (!nonTeaching && holidaySet.contains(date)) {
                nonTeaching = true; // CalendarHoliday always wins regardless of weekday
            }

            if (!nonTeaching) {
                teachingDates.add(date);
            }

            date = date.plusDays(1);
        }

        return teachingDates;
    }

    /**
     * ALTERNATE semantics (2nd & 4th Saturday/Sunday of the month) are a Phase-1
     * convention, not defined by Person A's schema or SOURCE_MATERIAL.md — see
     * LESSON_PLAN_CONTRACT.md section 2 for the explicit rationale.
     */
    public boolean resolveSaturdayRule(String rule, LocalDate date) {
        if (rule == null) {
            return true; // fail safe: unrecognized rule -> treat Saturday as non-teaching
        }
        switch (rule) {
            case "ALL_WORKING":
                return false;
            case "ALL_HOLIDAY":
                return true;
            case "ALTERNATE": {
                int n = ordinalOfMonth(date);
                return n == 2 || n == 4;
            }
            case "CUSTOM":
                // "admin decides case by case" — with no rule-detail field on
                // AcademicCalendar, CUSTOM delegates entirely to the holiday list.
                return false;
            default:
                return true;
        }
    }

    public boolean resolveSundayRule(String rule, LocalDate date) {
        if (rule == null) {
            return true;
        }
        switch (rule) {
            case "ALL_WORKING":
                return false;
            case "ALL_HOLIDAY":
                return true;
            case "ALTERNATE": {
                int n = ordinalOfMonth(date);
                return n == 2 || n == 4;
            }
            case "CUSTOM":
                return false;
            default:
                return true;
        }
    }

    private int ordinalOfMonth(LocalDate date) {
        return ((date.getDayOfMonth() - 1) / 7) + 1;
    }
}
