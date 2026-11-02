package com.easwari.backend.controller;

import com.easwari.backend.domain.AcademicCalendar;
import com.easwari.backend.domain.CalendarHoliday;
import com.easwari.backend.repository.AcademicCalendarRepository;
import com.easwari.backend.repository.CalendarHolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/calendar")
@RequiredArgsConstructor
public class CalendarController {

    private final AcademicCalendarRepository calendarRepository;
    private final CalendarHolidayRepository holidayRepository;
    private final com.easwari.backend.service.PdfExtractionService pdfExtractionService;

    @GetMapping
    public ResponseEntity<List<AcademicCalendar>> getAllCalendars() {
        return ResponseEntity.ok(calendarRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<AcademicCalendar> createCalendar(@RequestBody AcademicCalendar calendar) {
        if (calendar.getHolidays() != null) {
            for (CalendarHoliday h : calendar.getHolidays()) {
                h.setCalendar(calendar);
            }
        }
        AcademicCalendar saved = calendarRepository.save(calendar);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AcademicCalendar> updateCalendar(@PathVariable Long id, @RequestBody AcademicCalendar calendar) {
        if (!calendarRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        calendar.setId(id);
        if (calendar.getHolidays() != null) {
            for (CalendarHoliday h : calendar.getHolidays()) {
                h.setCalendar(calendar);
            }
        }
        AcademicCalendar saved = calendarRepository.save(calendar);
        return ResponseEntity.ok(saved);
    }

    @PostMapping("/{calendarId}/holidays")
    public ResponseEntity<CalendarHoliday> addHoliday(@PathVariable Long calendarId, @RequestBody CalendarHoliday holiday) {
        return calendarRepository.findById(calendarId).map(calendar -> {
            holiday.setCalendar(calendar);
            holiday.setUnexpected(true);
            CalendarHoliday saved = holidayRepository.save(holiday);
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Removes an unexpected holiday. Fixed government holidays cannot be deleted.
     */
    @DeleteMapping("/holidays/{holidayId}")
    public ResponseEntity<?> deleteHoliday(@PathVariable Long holidayId) {
        return holidayRepository.findById(holidayId).map(holiday -> {
            if (!holiday.isUnexpected()) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Fixed government/institution holidays cannot be edited or removed."));
            }
            holidayRepository.delete(holiday);
            return ResponseEntity.noContent().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Retrieves all holidays for a given year across all calendars and seeded government holidays.
     */
    @GetMapping("/holidays/year/{year}")
    public ResponseEntity<List<CalendarHoliday>> getHolidaysForYear(@PathVariable int year) {
        List<CalendarHoliday> holidays = holidayRepository.findAll().stream()
                .filter(h -> h.getHolidayDate() != null && h.getHolidayDate().getYear() == year)
                .sorted((a, b) -> a.getHolidayDate().compareTo(b.getHolidayDate()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(holidays);
    }

    /**
     * Extracts and auto-creates an Academic Calendar from an uploaded Schedule PDF/document.
     */
    @PostMapping(value = "/parse-schedule-document", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> parseScheduleDocument(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "academicYear", defaultValue = "2026-2027") String academicYear,
            @RequestParam(value = "semester", defaultValue = "3") Integer semester) {
        try {
            String text = pdfExtractionService.extractTextFromFile(file);
            Map<String, Object> extracted = pdfExtractionService.parseAcademicSchedule(text, academicYear, semester);
            return ResponseEntity.ok(extracted);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to parse schedule document: " + e.getMessage()));
        }
    }
}
