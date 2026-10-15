package com.odysseus.backend.service;

import com.odysseus.backend.domain.*;
import com.odysseus.backend.dto.TimetableValidationResult;
import com.odysseus.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
public class TimetableSolverAndValidationIntegrationTest {

    @Autowired
    private TimetableSolverService solverService;

    @Autowired
    private TimetableValidationService validationService;

    @Autowired
    private TimetableJobRepository jobRepository;

    @Autowired
    private TimetableEntryRepository entryRepository;

    @Autowired
    private TimetableAssignmentRepository assignmentRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private FacultyRepository facultyRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private FacultyAvailabilityRepository availabilityRepository;

    @Autowired
    private SubjectOfferingRepository offeringRepository;

    private String testJobId;

    @BeforeEach
    void setUp() {
        if (roomRepository.findByActiveTrue().isEmpty()) {
            roomRepository.save(Room.builder()
                    .roomCode("LH-101")
                    .roomName("Lecture Hall 101")
                    .roomType("LECTURE_HALL")
                    .capacity(60)
                    .active(true)
                    .build());
        }
        if (batchRepository.findByActiveTrue().isEmpty()) {
            batchRepository.save(Batch.builder()
                    .batchName("CSE-3-A")
                    .section("A")
                    .department("Computer Science Engineering")
                    .semester(3)
                    .strength(50)
                    .active(true)
                    .build());
        }
        if (subjectRepository.findByActiveTrue().isEmpty()) {
            subjectRepository.save(Subject.builder()
                    .subjectCode("CS301")
                    .subjectName("Data Structures")
                    .department("Computer Science Engineering")
                    .semester(3)
                    .subjectType("THEORY")
                    .theoryCredits(3.0)
                    .calculatedWeeklyHours(3.0)
                    .active(true)
                    .build());
        }
        if (facultyRepository.findByActiveTrue().isEmpty()) {
            Faculty f = Faculty.builder()
                    .employeeId("FAC-TEST-01")
                    .name("Dr. Test Professor")
                    .email("test.prof@odysseus.edu")
                    .department("Computer Science Engineering")
                    .maxWeeklyLoad(18)
                    .active(true)
                    .build();
            f.getEligibleSubjects().add(subjectRepository.findByActiveTrue().get(0));
            facultyRepository.save(f);
        }
        if (offeringRepository.findByActiveTrue().isEmpty()) {
            offeringRepository.save(SubjectOffering.builder()
                    .batch(batchRepository.findByActiveTrue().get(0))
                    .subject(subjectRepository.findByActiveTrue().get(0))
                    .assignedFaculty(facultyRepository.findByActiveTrue().get(0))
                    .academicYear("2026-2027")
                    .semester(3)
                    .active(true)
                    .build());
        }

        testJobId = UUID.randomUUID().toString();
        TimetableJob job = TimetableJob.builder()
                .id(testJobId)
                .status("QUEUED")
                .message("Test job queued")
                .build();
        jobRepository.save(job);
    }

    @Test
    @DisplayName("Should successfully solve timetable and satisfy all hard constraints without conflicts")
    void testTimetableGenerationSuccess() {
        solverService.solve(testJobId);

        TimetableJob job = jobRepository.findById(testJobId).orElseThrow();
        assertEquals("COMPLETED", job.getStatus(), "Expected solver status to be COMPLETED, got message: " + job.getMessage());

        List<TimetableAssignment> assignments = assignmentRepository.findByActiveTrue();
        assertFalse(assignments.isEmpty(), "Expected assignments to be persisted");

        // Validate the entire schedule using TimetableValidationService
        TimetableValidationResult validationResult = validationService.validateEntireSchedule(assignments);
        assertTrue(validationResult.isValid(), "Validation should pass with 0 errors: " + validationResult.getErrors());

        // Verify that entries were also persisted to legacy TimetableEntry repository
        List<TimetableEntry> entries = entryRepository.findByJobIdOrderByDayOfWeekAscPeriodNumberAsc(testJobId);
        assertFalse(entries.isEmpty(), "Expected timetable entries for job");
        int totalScheduledPeriodsInAssignments = assignments.stream()
                .mapToInt(a -> a.getDurationPeriods() != null ? a.getDurationPeriods() : 1)
                .sum();
        assertEquals(entries.size(), totalScheduledPeriodsInAssignments, "Total scheduled period counts should match");
    }

    @Test
    @DisplayName("Should enforce Faculty Availability as a hard constraint")
    void testFacultyAvailabilityEnforcement() {
        List<Faculty> facultyList = facultyRepository.findByActiveTrue();
        assertFalse(facultyList.isEmpty());
        Faculty f1 = facultyList.get(0);

        // Mark faculty unavailable on MONDAY period 1
        availabilityRepository.save(FacultyAvailability.builder()
                .faculty(f1)
                .dayOfWeek("MONDAY")
                .periodNumber(1)
                .available(false)
                .preferred(false)
                .build());

        solverService.solve(testJobId);

        List<TimetableAssignment> assignments = assignmentRepository.findByActiveTrue();
        List<TimetableAssignment> matched = assignments.stream()
                .filter(a -> a.getFaculty().getId().equals(f1.getId())
                        && "MONDAY".equalsIgnoreCase(a.getDayOfWeek())
                        && a.getPeriodNumber() == 1)
                .toList();

        if (!matched.isEmpty()) {
            for (TimetableAssignment ma : matched) {
                System.out.println("UNAVAIL_MATCH: id=" + ma.getId() + ", batch=" + ma.getBatch().getBatchName()
                        + ", subject=" + (ma.getSubject() != null ? ma.getSubject().getSubjectCode() : "null")
                        + ", activity=" + ma.getActivityType() + ", faculty=" + ma.getFaculty().getName());
            }
        }

        boolean scheduledWhileUnavailable = !matched.isEmpty();
        assertFalse(scheduledWhileUnavailable, "Faculty should never be scheduled in an unavailable slot");
    }

    @Test
    @DisplayName("Should reject invalid assignment proposal in TimetableValidationService")
    void testValidationRejectsRoomCapacityViolation() {
        List<Batch> batches = batchRepository.findByActiveTrue();
        List<Subject> subjects = subjectRepository.findByActiveTrue();
        List<Faculty> facultyList = facultyRepository.findByActiveTrue();

        Batch batch = batches.get(0);
        batch.setStrength(100);
        batchRepository.save(batch);

        Room smallRoom = roomRepository.save(Room.builder()
                .roomCode("TINY-1")
                .roomName("Tiny Room")
                .roomType("LECTURE_HALL")
                .capacity(30)
                .build());

        TimetableAssignment invalidProposal = TimetableAssignment.builder()
                .batch(batch)
                .subject(subjects.get(0))
                .faculty(facultyList.get(0))
                .room(smallRoom)
                .dayOfWeek("MONDAY")
                .periodNumber(1)
                .durationPeriods(1)
                .build();

        TimetableValidationResult result = validationService.validateProposedAssignment(invalidProposal, null);
        assertFalse(result.isValid(), "Should fail validation due to room capacity");
        assertEquals("ROOM_CAPACITY", result.getConflictType());
    }
}
