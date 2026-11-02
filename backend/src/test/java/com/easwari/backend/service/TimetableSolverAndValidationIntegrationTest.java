package com.easwari.backend.service;

import com.easwari.backend.domain.*;
import com.easwari.backend.dto.TimetableValidationResult;
import com.easwari.backend.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
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
    private Batch testBatch;
    private final List<Faculty> testFaculty = new ArrayList<>();
    private final List<Subject> testSubjects = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cleanUpData();

        if (roomRepository.findByActiveTrue().isEmpty()) {
            roomRepository.save(Room.builder()
                    .roomCode("LH-101")
                    .roomName("Lecture Hall 101")
                    .roomType("LECTURE_HALL")
                    .capacity(60)
                    .active(true)
                    .build());
        }

        testBatch = batchRepository.save(Batch.builder()
                .batchName("CSE-3-A")
                .section("A")
                .department("Computer Science Engineering")
                .semester(3)
                .strength(50)
                .academicYear("2026-2027")
                .active(true)
                .build());

        // Create 7 faculty members to ensure no load limit conflicts
        for (int i = 1; i <= 7; i++) {
            Faculty f = facultyRepository.save(Faculty.builder()
                    .employeeId("FAC-TEST-0" + i)
                    .name("Dr. Test Professor " + i)
                    .email("test.prof" + i + "@odysseus.edu")
                    .department("Computer Science Engineering")
                    .maxWeeklyLoad(20)
                    .active(true)
                    .build());
            testFaculty.add(f);
        }

        // Create subjects totaling 33 academic hours (6 theory @ 5h + 1 lab @ 3h)
        for (int i = 1; i <= 6; i++) {
            Subject s = subjectRepository.save(Subject.builder()
                    .subjectCode("CS30" + i)
                    .subjectName("CS Theory " + i)
                    .department("Computer Science Engineering")
                    .semester(3)
                    .subjectType("THEORY")
                    .theoryCredits(5.0)
                    .calculatedWeeklyHours(5.0)
                    .active(true)
                    .build());
            testSubjects.add(s);

            offeringRepository.save(SubjectOffering.builder()
                    .batch(testBatch)
                    .subject(s)
                    .assignedFaculty(testFaculty.get(i - 1))
                    .academicYear("2026-2027")
                    .semester(3)
                    .active(true)
                    .build());
        }

        Subject labSubject = subjectRepository.save(Subject.builder()
                .subjectCode("CS307L")
                .subjectName("CS Lab 1")
                .department("Computer Science Engineering")
                .semester(3)
                .subjectType("LAB")
                .labCredits(1.5)
                .calculatedWeeklyHours(3.0)
                .labDuration(3)
                .active(true)
                .build());
        testSubjects.add(labSubject);

        offeringRepository.save(SubjectOffering.builder()
                .batch(testBatch)
                .subject(labSubject)
                .assignedFaculty(testFaculty.get(6))
                .academicYear("2026-2027")
                .semester(3)
                .active(true)
                .build());

        testJobId = UUID.randomUUID().toString();
        TimetableJob job = TimetableJob.builder()
                .id(testJobId)
                .status("QUEUED")
                .message("Test job queued")
                .department("Computer Science Engineering")
                .semester(3)
                .build();
        jobRepository.save(job);
    }

    @AfterEach
    void tearDown() {
        cleanUpData();
    }

    private void cleanUpData() {
        availabilityRepository.deleteAll();
        assignmentRepository.deleteAll();
        entryRepository.deleteAll();
        offeringRepository.deleteAll();
        jobRepository.deleteAll();
        subjectRepository.findAll().stream()
                .filter(s -> "Computer Science Engineering".equals(s.getDepartment()))
                .forEach(subjectRepository::delete);
        facultyRepository.findAll().stream()
                .filter(f -> "Computer Science Engineering".equals(f.getDepartment()))
                .forEach(facultyRepository::delete);
        batchRepository.findAll().stream()
                .filter(b -> "Computer Science Engineering".equals(b.getDepartment()))
                .forEach(batchRepository::delete);
        testFaculty.clear();
        testSubjects.clear();
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
        List<Faculty> facultyList = testFaculty;
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
                .filter(a -> a.getFaculty() != null
                        && a.getFaculty().getId().equals(f1.getId())
                        && "MONDAY".equalsIgnoreCase(a.getDayOfWeek())
                        && a.getPeriodNumber() == 1)
                .toList();

        boolean scheduledWhileUnavailable = !matched.isEmpty();
        assertFalse(scheduledWhileUnavailable, "Faculty should never be scheduled in an unavailable slot");
    }

    @Test
    @DisplayName("Should reject invalid assignment proposal in TimetableValidationService")
    void testValidationRejectsRoomCapacityViolation() {
        Batch batch = testBatch;
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
                .subject(testSubjects.get(0))
                .faculty(testFaculty.get(0))
                .room(smallRoom)
                .dayOfWeek("MONDAY")
                .periodNumber(1)
                .durationPeriods(1)
                .build();

        TimetableValidationResult result = validationService.validateProposedAssignment(invalidProposal, null);
        assertFalse(result.isValid(), "Should fail validation due to room capacity");
        assertEquals("ROOM_CAPACITY", result.getConflictType());

        roomRepository.delete(smallRoom);
    }

    @Test
    @DisplayName("Library period is accepted without faculty and allocated to shared LIB-01 room")
    void testLibraryPeriodAcceptedWithoutFaculty() {
        Room libRoom = roomRepository.findByRoomCode("LIB-01").orElseGet(() -> roomRepository.save(Room.builder()
                .roomCode("LIB-01")
                .roomName("LIBRARY")
                .roomType("LECTURE_HALL")
                .capacity(80)
                .active(true)
                .build()));

        Subject libSubject = subjectRepository.findBySubjectCode("2321LIB301").orElseGet(() -> subjectRepository.save(Subject.builder()
                .subjectCode("2321LIB301")
                .subjectName("Library")
                .department("General")
                .subjectType("COMMON")
                .theoryCredits(1.0)
                .calculatedWeeklyHours(1.0)
                .active(true)
                .build()));

        TimetableAssignment libAssignment = TimetableAssignment.builder()
                .batch(testBatch)
                .subject(libSubject)
                .faculty(null)
                .room(libRoom)
                .dayOfWeek("WEDNESDAY")
                .periodNumber(3)
                .durationPeriods(1)
                .activityType("COMMON")
                .active(true)
                .build();

        TimetableValidationResult result = validationService.validateProposedAssignment(libAssignment, null);
        assertTrue(result.isValid(), "Library common period should be valid without faculty: " + result.getErrors());

        TimetableValidationResult fullResult = validationService.validateEntireSchedule(List.of(libAssignment));
        assertTrue(fullResult.isValid(), "Entire schedule validator must not reject Library as placeholder: " + fullResult.getErrors());
    }

    @Test
    @DisplayName("LIB-01 shared capacity respects 80 seats across sections and departments")
    void testLibrarySharedRoomCapacityConstraint() {
        Room libRoom = roomRepository.findByRoomCode("LIB-01").orElseGet(() -> roomRepository.save(Room.builder()
                .roomCode("LIB-01")
                .roomName("LIBRARY")
                .roomType("LECTURE_HALL")
                .capacity(80)
                .active(true)
                .build()));

        Subject libSubject = subjectRepository.findBySubjectCode("2321LIB301").orElseGet(() -> subjectRepository.save(Subject.builder()
                .subjectCode("2321LIB301")
                .subjectName("Library")
                .department("General")
                .subjectType("COMMON")
                .theoryCredits(1.0)
                .calculatedWeeklyHours(1.0)
                .active(true)
                .build()));

        Batch eceBatch = batchRepository.save(Batch.builder()
                .batchName("ECE-3-A")
                .section("A")
                .department("Electronics and Communication Engineering")
                .semester(3)
                .strength(50)
                .academicYear("2026-2027")
                .active(true)
                .build());

        // Batch 1 (strength 50) and Batch 2 (strength 50) in same slot = 100 > 80 capacity -> INVALID
        TimetableAssignment assign1 = TimetableAssignment.builder()
                .batch(testBatch)
                .subject(libSubject)
                .room(libRoom)
                .dayOfWeek("MONDAY")
                .periodNumber(2)
                .durationPeriods(1)
                .active(true)
                .build();

        TimetableAssignment assign2OverCapacity = TimetableAssignment.builder()
                .batch(eceBatch)
                .subject(libSubject)
                .room(libRoom)
                .dayOfWeek("MONDAY")
                .periodNumber(2)
                .durationPeriods(1)
                .active(true)
                .build();

        TimetableValidationResult clashResult = validationService.validateEntireSchedule(List.of(assign1, assign2OverCapacity));
        assertFalse(clashResult.isValid(), "Two sections exceeding room capacity (50+50 > 80) must be rejected");

        // When scheduled at different periods -> VALID
        TimetableAssignment assign2DifferentSlot = TimetableAssignment.builder()
                .batch(eceBatch)
                .subject(libSubject)
                .room(libRoom)
                .dayOfWeek("TUESDAY")
                .periodNumber(2)
                .durationPeriods(1)
                .active(true)
                .build();

        TimetableValidationResult validResult = validationService.validateEntireSchedule(List.of(assign1, assign2DifferentSlot));
        assertTrue(validResult.isValid(), "Two sections in separate slots must be valid: " + validResult.getErrors());

        batchRepository.delete(eceBatch);
    }

    @Test
    @DisplayName("Genuine placeholder periods like FREE or SELF-STUDY are still strictly rejected")
    void testGenuinePlaceholdersStillRejected() {
        Subject freeSubject = subjectRepository.save(Subject.builder()
                .subjectCode("FREE-01")
                .subjectName("Free Period")
                .department("General")
                .subjectType("THEORY")
                .active(true)
                .build());

        TimetableAssignment freeAssignment = TimetableAssignment.builder()
                .batch(testBatch)
                .subject(freeSubject)
                .dayOfWeek("MONDAY")
                .periodNumber(3)
                .durationPeriods(1)
                .active(true)
                .build();

        TimetableValidationResult result = validationService.validateEntireSchedule(List.of(freeAssignment));
        assertFalse(result.isValid(), "FREE periods must be strictly rejected as placeholders");
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("Placeholder period")),
                "Error message should mention placeholder rejection");

        subjectRepository.delete(freeSubject);
    }
}
