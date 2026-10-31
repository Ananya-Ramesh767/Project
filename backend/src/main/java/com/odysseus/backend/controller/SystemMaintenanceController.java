package com.odysseus.backend.controller;

import com.odysseus.backend.domain.CourseObjective;
import com.odysseus.backend.domain.CourseOutcome;
import com.odysseus.backend.domain.SyllabusUnit;
import com.odysseus.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/system")
@RequiredArgsConstructor
@Slf4j
public class SystemMaintenanceController {

    private final TimetableAssignmentRepository assignmentRepository;
    private final TimetableEntryRepository entryRepository;
    private final TimetableJobRepository jobRepository;
    private final LessonPlanSessionRepository lessonPlanSessionRepository;
    private final LessonPlanRepository lessonPlanRepository;
    private final SyllabusUnitRepository syllabusUnitRepository;
    private final CourseOutcomeRepository courseOutcomeRepository;
    private final CourseObjectiveRepository courseObjectiveRepository;
    private final SubjectOfferingRepository offeringRepository;
    private final FacultyAvailabilityRepository facultyAvailabilityRepository;
    private final FacultyRepository facultyRepository;
    private final SubjectRepository subjectRepository;
    private final RoomChangeHistoryRepository roomChangeHistoryRepository;
    private final BatchRepository batchRepository;
    private final AcademicCalendarRepository calendarRepository;
    private final CalendarHolidayRepository holidayRepository;
    private final RoomRepository roomRepository;
    private final javax.sql.DataSource dataSource;

    @PostMapping("/backup")
    public ResponseEntity<?> backupDatabase() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            new java.io.File("backend/data/backup_phase1").mkdirs();
            new java.io.File("data/backup_phase1").mkdirs();
            stmt.execute("BACKUP TO 'data/backup_phase1/odysseus_db_backup.zip'");
            log.info("[SystemMaintenance] Database backed up to data/backup_phase1/odysseus_db_backup.zip");
            return ResponseEntity.ok(Map.of("status", "success", "file", "data/backup_phase1/odysseus_db_backup.zip"));
        } catch (Exception e) {
            log.error("[SystemMaintenance] Backup error: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/clear-data")
    @DeleteMapping("/clear-data")
    @Transactional
    public ResponseEntity<?> clearStoredData(
            @RequestParam(required = false, defaultValue = "false") boolean includeRooms,
            @RequestParam(required = false, defaultValue = "false") boolean includeCalendar,
            @RequestParam(required = false, defaultValue = "false") boolean clearBatches) {
        log.info("[SystemMaintenance] Clearing stored data (includeRooms={}, includeCalendar={}, clearBatches={})...",
                includeRooms, includeCalendar, clearBatches);

        // 1. Delete Timetables and Jobs
        assignmentRepository.deleteAll();
        entryRepository.deleteAll();
        jobRepository.deleteAll();
        roomChangeHistoryRepository.deleteAll();

        // 2. Delete Lesson Plans and Syllabus
        lessonPlanSessionRepository.deleteAll();
        lessonPlanRepository.deleteAll();
        courseObjectiveRepository.deleteAll();
        courseOutcomeRepository.deleteAll();
        syllabusUnitRepository.deleteAll();

        // 3. Delete Subject Offerings
        offeringRepository.deleteAll();

        // 4. Delete Faculty and Availability
        facultyAvailabilityRepository.deleteAll();
        facultyRepository.findAll().forEach(f -> {
            if (f.getEligibleSubjects() != null) {
                f.getEligibleSubjects().clear();
                facultyRepository.save(f);
            }
        });
        facultyRepository.deleteAll();

        // 5. Delete Subjects
        subjectRepository.deleteAll();

        // 6. Reset or Delete Batches
        if (clearBatches) {
            batchRepository.deleteAll();
        } else {
            // Unassign all rooms from batches per Phase 2 rules
            batchRepository.findAll().forEach(b -> {
                b.setAssignedRoomCode(null);
                b.setAssignedBlockName(null);
                b.setAssignedFloorNumber(null);
                batchRepository.save(b);
            });
        }

        // Reset room assigned sections
        roomRepository.findAll().forEach(r -> {
            if (r.getAssignedSection() != null) {
                r.setAssignedSection(null);
                roomRepository.save(r);
            }
        });

        // 7. Optional: Calendar & Holidays
        if (includeCalendar) {
            holidayRepository.deleteAll();
            calendarRepository.deleteAll();
        }

        // 8. Optional: Rooms
        if (includeRooms) {
            roomRepository.deleteAll();
        }

        log.info("[SystemMaintenance] Stored data successfully purged.");

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "All requested stored data has been cleared.",
                "remainingBatches", batchRepository.count(),
                "remainingSubjects", subjectRepository.count(),
                "remainingFaculty", facultyRepository.count(),
                "remainingOfferings", offeringRepository.count(),
                "remainingRooms", roomRepository.count()
        ));
    }

    @GetMapping("/inspect-syllabus")
    public ResponseEntity<?> inspectSyllabus() {
        List<SyllabusUnit> units = syllabusUnitRepository.findAll();
        List<CourseObjective> objs = courseObjectiveRepository.findAll();
        List<CourseOutcome> cos = courseOutcomeRepository.findAll();
        return ResponseEntity.ok(Map.of(
                "totalUnits", units.size(),
                "totalObjectives", objs.size(),
                "totalOutcomes", cos.size(),
                "units", units.stream().map(u -> Map.of(
                        "id", u.getId(),
                        "subjectId", u.getSubject() != null ? u.getSubject().getId() : null,
                        "title", u.getTitle(),
                        "unitNumber", u.getUnitNumber(),
                        "active", u.isActive()
                )).collect(java.util.stream.Collectors.toList())
        ));
    }
}
