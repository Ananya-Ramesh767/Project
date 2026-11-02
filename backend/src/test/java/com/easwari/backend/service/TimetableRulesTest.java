package com.easwari.backend.service;

import com.easwari.backend.domain.*;
import com.easwari.backend.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class TimetableRulesTest {

    @Autowired
    private TimetableSolverService solverService;

    @Autowired
    private TimetableJobRepository jobRepository;

    @Autowired
    private TimetableAssignmentRepository assignmentRepository;

    @Autowired
    private TimetableEntryRepository entryRepository;

    @Autowired
    private BatchRepository batchRepository;

    @Autowired
    private FacultyRepository facultyRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private SubjectOfferingRepository offeringRepository;

    private static final String TEST_DEPT = "RulesTest Engineering";
    private static final int TEST_SEM = 3;

    private Batch testBatch;
    private Room homeRoom;
    private Room labRoom;
    private final List<Faculty> testFacultyList = new ArrayList<>();
    private final List<Subject> testSubjectList = new ArrayList<>();
    private final List<SubjectOffering> testOfferings = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cleanTestData();

        // 1. Dedicated Home Classroom
        homeRoom = roomRepository.save(Room.builder()
                .roomCode("RULES-LH101")
                .roomName("Rules Test Lecture Hall")
                .roomType("LECTURE_HALL")
                .blockName("Academic Block")
                .floorNumber(1)
                .capacity(70)
                .active(true)
                .build());

        // 2. Lab Room
        labRoom = roomRepository.save(Room.builder()
                .roomCode("RULES-LAB101")
                .roomName("Rules Test Computing Lab")
                .roomType("LAB")
                .blockName("Academic Block")
                .floorNumber(1)
                .capacity(70)
                .active(true)
                .build());

        // 3. Batch
        testBatch = batchRepository.save(Batch.builder()
                .batchName("TEST-SEC-A")
                .section("A")
                .department(TEST_DEPT)
                .semester(TEST_SEM)
                .strength(50)
                .academicYear("2026-2027")
                .active(true)
                .build());

        // 4. Faculty members
        for (int i = 1; i <= 8; i++) {
            Faculty f = facultyRepository.save(Faculty.builder()
                    .employeeId("FAC-RULES-" + i)
                    .name("Rules Prof " + i)
                    .email("prof" + i + "@rulestest.edu")
                    .department(TEST_DEPT)
                    .maxWeeklyLoad(20)
                    .active(true)
                    .build());
            testFacultyList.add(f);
        }
    }

    @AfterEach
    void tearDown() {
        cleanTestData();
    }

    private void cleanTestData() {
        assignmentRepository.deleteAll();
        entryRepository.deleteAll();
        offeringRepository.deleteAll();
        jobRepository.deleteAll();
        subjectRepository.findAll().stream()
                .filter(s -> TEST_DEPT.equals(s.getDepartment()))
                .forEach(subjectRepository::delete);
        facultyRepository.findAll().stream()
                .filter(f -> TEST_DEPT.equals(f.getDepartment()))
                .forEach(facultyRepository::delete);
        batchRepository.findAll().stream()
                .filter(b -> TEST_DEPT.equals(b.getDepartment()))
                .forEach(batchRepository::delete);
        roomRepository.findAll().stream()
                .filter(r -> r.getRoomCode().startsWith("RULES-"))
                .forEach(roomRepository::delete);
    }

    /**
     * Seeds offerings totaling 33 academic periods:
     * - 5 theory subjects @ 5, 5, 5, 5, 5 = 25 periods
     * - 1 theory subject @ 5 periods = 5 periods (total 30)
     * - 1 lab subject @ 3 periods = 3 periods (total 33)
     * Plus 7 institutional extras = 40 periods total.
     */
    private void seed33AcademicHours() {
        int[] hours = {5, 5, 5, 5, 5, 5};
        for (int i = 0; i < hours.length; i++) {
            Subject s = subjectRepository.save(Subject.builder()
                    .subjectCode("TH-SUB-" + (i + 1))
                    .subjectName("Theory Subject " + (i + 1))
                    .department(TEST_DEPT)
                    .semester(TEST_SEM)
                    .subjectType("THEORY")
                    .theoryCredits((double) hours[i])
                    .calculatedWeeklyHours((double) hours[i])
                    .active(true)
                    .build());
            testSubjectList.add(s);

            SubjectOffering off = offeringRepository.save(SubjectOffering.builder()
                    .batch(testBatch)
                    .subject(s)
                    .assignedFaculty(testFacultyList.get(i))
                    .academicYear("2026-2027")
                    .semester(TEST_SEM)
                    .active(true)
                    .build());
            testOfferings.add(off);
        }

        // 1 lab subject with 3 hours (labDuration = 3)
        Subject labSub = subjectRepository.save(Subject.builder()
                .subjectCode("LAB-SUB-1")
                .subjectName("Lab Subject 1")
                .department(TEST_DEPT)
                .semester(TEST_SEM)
                .subjectType("LAB")
                .labCredits(1.5)
                .calculatedWeeklyHours(3.0)
                .labDuration(3)
                .active(true)
                .build());
        testSubjectList.add(labSub);

        SubjectOffering labOff = offeringRepository.save(SubjectOffering.builder()
                .batch(testBatch)
                .subject(labSub)
                .assignedFaculty(testFacultyList.get(6))
                .academicYear("2026-2027")
                .semester(TEST_SEM)
                .active(true)
                .build());
        testOfferings.add(labOff);
    }

    @Test
    @DisplayName("Rule 40 Periods: Exactly 40 filled periods scheduled per section when offerings = 33h")
    void testFortyPeriodRuleSuccess() {
        seed33AcademicHours();

        String jobId = UUID.randomUUID().toString();
        TimetableJob job = jobRepository.save(TimetableJob.builder()
                .id(jobId)
                .status("QUEUED")
                .message("Queued")
                .department(TEST_DEPT)
                .semester(TEST_SEM)
                .build());

        solverService.solve(jobId);

        TimetableJob completedJob = jobRepository.findById(jobId).orElseThrow();
        assertEquals("COMPLETED", completedJob.getStatus(), "Solver should complete successfully: " + completedJob.getMessage());

        List<TimetableAssignment> assignments = assignmentRepository.findByBatchIdAndActiveTrue(testBatch.getId());
        int totalScheduledPeriods = assignments.stream()
                .mapToInt(a -> a.getDurationPeriods() != null ? a.getDurationPeriods() : 1)
                .sum();

        assertEquals(40, totalScheduledPeriods, "Each section must have exactly 40 filled periods (33 academic + 7 extra)");

        // Verify that every period (Mon-Fri, P1-P8) is accounted for
        String[] days = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"};
        Set<String> scheduledSlots = new HashSet<>();
        for (TimetableAssignment a : assignments) {
            int duration = a.getDurationPeriods() != null ? a.getDurationPeriods() : 1;
            for (int p = 0; p < duration; p++) {
                String key = a.getDayOfWeek() + "-P" + (a.getPeriodNumber() + p);
                assertTrue(scheduledSlots.add(key), "Slot should not be duplicated: " + key);
            }
        }
        for (String day : days) {
            for (int p = 1; p <= 8; p++) {
                assertTrue(scheduledSlots.contains(day + "-P" + p), "Missing slot: " + day + "-P" + p);
            }
        }
    }

    @Test
    @DisplayName("Rule 40 Periods: Shortfall marks job INFEASIBLE and names the section and shortfall count")
    void testFortyPeriodRuleShortfallInfeasible() {
        // Seed only 15 academic hours (shortfall = 33 - 15 = 18 periods)
        Subject s = subjectRepository.save(Subject.builder()
                .subjectCode("DEFICIENT-1")
                .subjectName("Deficient Subject")
                .department(TEST_DEPT)
                .semester(TEST_SEM)
                .subjectType("THEORY")
                .theoryCredits(15.0)
                .calculatedWeeklyHours(15.0)
                .active(true)
                .build());

        offeringRepository.save(SubjectOffering.builder()
                .batch(testBatch)
                .subject(s)
                .assignedFaculty(testFacultyList.get(0))
                .academicYear("2026-2027")
                .semester(TEST_SEM)
                .active(true)
                .build());

        String jobId = UUID.randomUUID().toString();
        jobRepository.save(TimetableJob.builder()
                .id(jobId)
                .status("QUEUED")
                .message("Queued")
                .department(TEST_DEPT)
                .semester(TEST_SEM)
                .build());

        solverService.solve(jobId);

        TimetableJob resultJob = jobRepository.findById(jobId).orElseThrow();
        assertEquals("INFEASIBLE", resultJob.getStatus(), "Deficient academic hours must mark the job INFEASIBLE");
        assertTrue(resultJob.getMessage().contains("shortfall"), "Message must report shortfall");
        assertTrue(resultJob.getMessage().contains(testBatch.getBatchName()), "Message must name the section");
    }

    @Test
    @DisplayName("Rule Fixed Slots: AT (Tue P1), TT (Thu P1), LC (Mon P7), COUN (Mon P8), NPTEL (Wed P6), CSD (Fri P4-P5) in home classroom")
    void testFixedSpecialSlotsPlacement() {
        seed33AcademicHours();

        String jobId = UUID.randomUUID().toString();
        jobRepository.save(TimetableJob.builder()
                .id(jobId)
                .status("QUEUED")
                .message("Queued")
                .department(TEST_DEPT)
                .semester(TEST_SEM)
                .build());

        solverService.solve(jobId);

        List<TimetableAssignment> assignments = assignmentRepository.findByBatchIdAndActiveTrue(testBatch.getId());

        // 1. Aptitude Test (AT): Tuesday P1
        Optional<TimetableAssignment> at = assignments.stream()
                .filter(a -> "TUESDAY".equalsIgnoreCase(a.getDayOfWeek()) && a.getPeriodNumber() == 1)
                .findFirst();
        assertTrue(at.isPresent(), "AT must be placed on Tuesday Period 1");
        assertNotEquals("LAB", at.get().getRoom().getRoomType(), "AT must not use a LAB room");

        // 2. Technical Test (TT): Thursday P1
        Optional<TimetableAssignment> tt = assignments.stream()
                .filter(a -> "THURSDAY".equalsIgnoreCase(a.getDayOfWeek()) && a.getPeriodNumber() == 1)
                .findFirst();
        assertTrue(tt.isPresent(), "TT must be placed on Thursday Period 1");
        assertNotEquals("LAB", tt.get().getRoom().getRoomType(), "TT must not use a LAB room");

        // 3. LeetCode (LC): Monday P7
        Optional<TimetableAssignment> lc = assignments.stream()
                .filter(a -> "MONDAY".equalsIgnoreCase(a.getDayOfWeek()) && a.getPeriodNumber() == 7)
                .findFirst();
        assertTrue(lc.isPresent(), "LC must be placed on Monday Period 7");
        assertNotEquals("LAB", lc.get().getRoom().getRoomType(), "LC must not use a LAB room");

        // 4. Counselling (COUN): Monday P8
        Optional<TimetableAssignment> coun = assignments.stream()
                .filter(a -> "MONDAY".equalsIgnoreCase(a.getDayOfWeek()) && a.getPeriodNumber() == 8)
                .findFirst();
        assertTrue(coun.isPresent(), "COUN must be placed on Monday Period 8");
        assertNotEquals("LAB", coun.get().getRoom().getRoomType(), "COUN must not use a LAB room");

        // 5. NPTEL: Wednesday P6
        Optional<TimetableAssignment> nptel = assignments.stream()
                .filter(a -> "WEDNESDAY".equalsIgnoreCase(a.getDayOfWeek()) && a.getPeriodNumber() == 6)
                .findFirst();
        assertTrue(nptel.isPresent(), "NPTEL must be placed on Wednesday Period 6");
        assertNotEquals("LAB", nptel.get().getRoom().getRoomType(), "NPTEL must not use a LAB room");

        // 6. CSD: Friday P4-P5 (2 consecutive periods, activityType CAREER_SKILL_DEVELOPMENT)
        Optional<TimetableAssignment> csd = assignments.stream()
                .filter(a -> "FRIDAY".equalsIgnoreCase(a.getDayOfWeek()) && a.getPeriodNumber() == 4 && a.getDurationPeriods() == 2)
                .findFirst();
        assertTrue(csd.isPresent(), "CSD must be placed on Friday Period 4 for 2 consecutive periods");
        assertEquals("CAREER_SKILL_DEVELOPMENT", csd.get().getActivityType(), "CSD must have activityType CAREER_SKILL_DEVELOPMENT");
        assertNotEquals("LAB", csd.get().getRoom().getRoomType(), "CSD must not use a LAB room");
    }

    @Test
    @DisplayName("Rule Lab Windows: Allowed windows only, never cross break or lunch, max 1 lab block per section/day")
    void testLabWindowsConstraints() {
        seed33AcademicHours();

        String jobId = UUID.randomUUID().toString();
        jobRepository.save(TimetableJob.builder()
                .id(jobId)
                .status("QUEUED")
                .message("Queued")
                .department(TEST_DEPT)
                .semester(TEST_SEM)
                .build());

        solverService.solve(jobId);

        List<TimetableAssignment> assignments = assignmentRepository.findByBatchIdAndActiveTrue(testBatch.getId());
        List<TimetableAssignment> labBlocks = assignments.stream()
                .filter(a -> a.isLabBlock() || "LAB".equalsIgnoreCase(a.getActivityType()))
                .filter(a -> a.getDurationPeriods() != null && a.getDurationPeriods() >= 2)
                .toList();

        assertFalse(labBlocks.isEmpty(), "At least one lab block should be scheduled");

        Map<String, Integer> labBlocksPerDay = new HashMap<>();
        for (TimetableAssignment lab : labBlocks) {
            String day = lab.getDayOfWeek();
            labBlocksPerDay.put(day, labBlocksPerDay.getOrDefault(day, 0) + 1);
            assertTrue(labBlocksPerDay.get(day) <= 1, "At most 1 lab block allowed per section per day on " + day);

            int start = lab.getPeriodNumber();
            int duration = lab.getDurationPeriods();
            int end = start + duration - 1;

            // Never cross morning break (break is after P2, so start <= 2 and end >= 3 is forbidden)
            assertFalse(start <= 2 && end >= 3, "Lab block " + start + "-" + end + " must not cross morning break (after P2)");

            // Never cross lunch (lunch is after P5, so start <= 5 and end >= 6 is forbidden)
            assertFalse(start <= 5 && end >= 6, "Lab block " + start + "-" + end + " must not cross lunch break (after P5)");

            // Allowed windows: {1,2}, {3,4}, {4,5}, {6,7}, {7,8} for 2 periods; {3,4,5}, {6,7,8} for 3 periods
            if (duration == 3) {
                assertTrue((start == 3 && end == 5) || (start == 6 && end == 8),
                        "3-period lab must use window P3-5 or P6-8, got P" + start + "-" + end);
            } else if (duration == 2) {
                boolean valid2Period = (start == 1 && end == 2) || (start == 3 && end == 4) ||
                        (start == 4 && end == 5) || (start == 6 && end == 7) || (start == 7 && end == 8);
                assertTrue(valid2Period, "2-period lab must use allowed window, got P" + start + "-" + end);
            }
        }
    }
}
