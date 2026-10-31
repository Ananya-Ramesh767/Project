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

    @RequestMapping(value = "/clear-data", method = {RequestMethod.POST, RequestMethod.DELETE})
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

    @PostMapping("/cleanup-duplicate-batches")
    @Transactional
    public ResponseEntity<?> cleanupDuplicateBatches() {
        log.info("[SystemMaintenance] Starting safe duplicate batches cleanup...");

        List<com.odysseus.backend.domain.Batch> allBatches = batchRepository.findAll();
        List<com.odysseus.backend.domain.SubjectOffering> allOfferings = offeringRepository.findAll();

        Map<Long, Long> canonicalMap = Map.ofEntries(
                Map.entry(65L, 1L), Map.entry(66L, 2L), Map.entry(67L, 3L), Map.entry(68L, 4L),
                Map.entry(69L, 5L), Map.entry(70L, 6L), Map.entry(71L, 7L),
                Map.entry(97L, 33L), Map.entry(98L, 34L), Map.entry(99L, 35L), Map.entry(100L, 36L),
                Map.entry(101L, 37L), Map.entry(102L, 38L), Map.entry(103L, 39L)
        );

        int offeringsMerged = 0;
        int offeringsDeleted = 0;
        int batchesUpdated = 0;
        int batchesRemoved = 0;

        // 1. Normalize department name on canonical batches (1..7, 33..39)
        for (Long canonId : List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 33L, 34L, 35L, 36L, 37L, 38L, 39L)) {
            batchRepository.findById(canonId).ifPresent(b -> {
                b.setDepartment("Computer Science and Engineering");
                batchRepository.save(b);
            });
            batchesUpdated++;
        }

        // 2. Process duplicate batches and their offerings
        for (Map.Entry<Long, Long> entry : canonicalMap.entrySet()) {
            Long dupId = entry.getKey();
            Long canonId = entry.getValue();

            var dupBatch = batchRepository.findById(dupId).orElse(null);
            var canonBatch = batchRepository.findById(canonId).orElse(null);

            if (dupBatch != null && canonBatch != null) {
                List<com.odysseus.backend.domain.SubjectOffering> dupOfferings = allOfferings.stream()
                        .filter(o -> o.getBatch() != null && o.getBatch().getId().equals(dupId))
                        .toList();

                List<com.odysseus.backend.domain.SubjectOffering> canonOfferings = allOfferings.stream()
                        .filter(o -> o.getBatch() != null && o.getBatch().getId().equals(canonId))
                        .toList();

                for (var dOff : dupOfferings) {
                    var cOffOpt = canonOfferings.stream()
                            .filter(co -> co.getSubject() != null && dOff.getSubject() != null && co.getSubject().getId().equals(dOff.getSubject().getId()))
                            .findFirst();

                    if (cOffOpt.isPresent()) {
                        var cOff = cOffOpt.get();
                        if (cOff.getAssignedFaculty() == null && dOff.getAssignedFaculty() != null) {
                            cOff.setAssignedFaculty(dOff.getAssignedFaculty());
                            offeringRepository.save(cOff);
                            offeringsMerged++;
                        }
                        offeringRepository.delete(dOff);
                        offeringsDeleted++;
                    } else {
                        dOff.setBatch(canonBatch);
                        offeringRepository.save(dOff);
                        offeringsMerged++;
                    }
                }

                batchRepository.delete(dupBatch);
                batchesRemoved++;
            }
        }

        log.info("[SystemMaintenance] Cleanup completed: {} batches normalized, {} duplicate batches removed, {} offerings merged, {} duplicate offerings deleted.",
                batchesUpdated, batchesRemoved, offeringsMerged, offeringsDeleted);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "canonicalBatchesRetained", batchesUpdated,
                "duplicateBatchesRemoved", batchesRemoved,
                "offeringsMerged", offeringsMerged,
                "offeringsDeleted", offeringsDeleted,
                "remainingBatches", batchRepository.count(),
                "remainingOfferings", offeringRepository.count()
        ));
    }
}
