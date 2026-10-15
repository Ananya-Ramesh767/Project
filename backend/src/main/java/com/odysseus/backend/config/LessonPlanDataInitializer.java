package com.odysseus.backend.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.odysseus.backend.domain.*;
import com.odysseus.backend.repository.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Additive seed loader for the Lesson Plan module (Person C). Kept as its own
 * CommandLineRunner, separate from Person A's {@link DataInitializer}, so their
 * existing seedUsers/seedDefaultNepRule/seedCollegeTimeslots/seedAcademicCalendar
 * methods are never touched.
 *
 * Loads docs/lesson-plan/seed-syllabus-data.json (copied to the classpath at
 * backend/src/main/resources/lesson-plan/seed-syllabus-data.json) per
 * LESSON_PLAN_CONTRACT.md sections 5.1 and 7, resolves/creates the two Subject
 * rows by subjectCode, and inserts their SyllabusUnit/CourseOutcome/CourseObjective
 * child rows. Also seeds:
 *  - a second, additive AcademicCalendar for the real Semester-III 2026-27 window
 *    (06.07.2026-30.10.2026, SOURCE_MATERIAL.md section 5) alongside — not
 *    replacing — Person A's existing placeholder Semester-5 demo calendar;
 *  - a Batch + Faculty + SubjectOffering for the OOP-Java course (2321CSC304R) so
 *    there is a real, runnable end-to-end path (seed data -> generate -> fetch).
 *
 * All of this is idempotent: re-running the app never creates duplicate rows.
 */
// @Component — disabled: all lesson-plan / syllabus data is entered by the admin; no demo seed.
@RequiredArgsConstructor
public class LessonPlanDataInitializer implements CommandLineRunner {

    private static final String SEED_RESOURCE_PATH = "lesson-plan/seed-syllabus-data.json";

    private final SubjectRepository subjectRepository;
    private final SyllabusUnitRepository syllabusUnitRepository;
    private final CourseOutcomeRepository courseOutcomeRepository;
    private final CourseObjectiveRepository courseObjectiveRepository;
    private final AcademicCalendarRepository academicCalendarRepository;
    private final BatchRepository batchRepository;
    private final FacultyRepository facultyRepository;
    private final SubjectOfferingRepository subjectOfferingRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) throws Exception {
        SeedFile seedFile = loadSeedFile();
        if (seedFile == null || seedFile.subjects == null) {
            return;
        }

        for (SeedSubject seedSubject : seedFile.subjects) {
            Subject subject = resolveOrCreateSubject(seedSubject);
            seedSyllabusForSubject(subject, seedSubject);
        }

        AcademicCalendar semester3Calendar = seedSemester3AcademicCalendar();
        seedOopJavaEndToEndFixtures(semester3Calendar);
    }

    private SeedFile loadSeedFile() throws Exception {
        ClassPathResource resource = new ClassPathResource(SEED_RESOURCE_PATH);
        if (!resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readValue(in, SeedFile.class);
        }
    }

    private Subject resolveOrCreateSubject(SeedSubject seed) {
        return subjectRepository.findBySubjectCode(seed.subjectCode).orElseGet(() -> {
            Map<String, Integer> ltpr = seed.periodsPerWeek != null ? seed.periodsPerWeek : Map.of();
            Integer l = ltpr.get("L");
            Integer t = ltpr.get("T");
            Integer p = ltpr.get("P");
            Integer r = ltpr.get("R");
            boolean requiresLab = p != null && p > 0;

            double theoryHours = (l != null ? l : 0.0) + (r != null ? r : 0.0);
            double labHours = (p != null ? p : 0.0);
            double tutorialHours = (t != null ? t : 0.0);
            double totalHours = theoryHours + labHours + tutorialHours;
            if (totalHours <= 0 && seed.credits != null) {
                totalHours = seed.credits.doubleValue();
                theoryHours = totalHours;
            }

            Subject subject = Subject.builder()
                    .subjectCode(seed.subjectCode)
                    .subjectName(seed.subjectName)
                    .department("CSE")
                    .semester(seed.semester)
                    .subjectType(requiresLab ? "THEORY_AND_LAB" : "THEORY")
                    .requiresLab(requiresLab)
                    .labDuration(requiresLab ? 2 : null)
                    .theoryCredits(theoryHours)
                    .labCredits(labHours > 0 ? labHours / 2.0 : 0.0)
                    .tutorialCredits(tutorialHours)
                    .calculatedWeeklyHours(totalHours > 0 ? totalHours : 3.0)
                    .weeklyLecturePeriods(l)
                    .weeklyTutorialPeriods(t)
                    .weeklyPracticalPeriods(p)
                    .weeklyProjectPeriods(r)
                    .curriculumCredits(seed.credits)
                    .active(true)
                    .build();
            return subjectRepository.save(subject);
        });
    }

    private void seedSyllabusForSubject(Subject subject, SeedSubject seed) {
        if (courseObjectiveRepository.findBySubjectIdAndActiveTrueOrderBySequenceNumberAsc(subject.getId()).isEmpty()
                && seed.objectives != null) {
            for (SeedObjective o : seed.objectives) {
                courseObjectiveRepository.save(CourseObjective.builder()
                        .subject(subject)
                        .sequenceNumber(o.sequenceNumber)
                        .description(o.description)
                        .active(true)
                        .build());
            }
        }

        if (courseOutcomeRepository.findBySubjectIdAndActiveTrueOrderByCoNumberAsc(subject.getId()).isEmpty()
                && seed.outcomes != null) {
            for (SeedOutcome o : seed.outcomes) {
                courseOutcomeRepository.save(CourseOutcome.builder()
                        .subject(subject)
                        .coNumber(o.coNumber)
                        .description(o.description)
                        .active(true)
                        .build());
            }
        }

        if (syllabusUnitRepository.findBySubjectIdAndActiveTrueOrderByUnitNumberAsc(subject.getId()).isEmpty()
                && seed.units != null) {
            for (Map.Entry<String, List<SeedUnit>> trackEntry : seed.units.entrySet()) {
                String sessionType = trackEntry.getKey();
                for (SeedUnit u : trackEntry.getValue()) {
                    syllabusUnitRepository.save(SyllabusUnit.builder()
                            .subject(subject)
                            .sessionType(sessionType)
                            .unitNumber(u.unitNumber)
                            .title(u.title)
                            .topics(u.topics != null ? new ArrayList<>(u.topics) : new ArrayList<>())
                            .periodsAllotted(u.periodsAllotted)
                            .periodsAllottedIsDerived(u.periodsAllottedIsDerived)
                            .derivationNote(u.derivationNote)
                            .active(true)
                            .build());
                }
            }
        }
    }

    private AcademicCalendar seedSemester3AcademicCalendar() {
        String academicYear = "2026-2027";
        int semester = 3;
        LocalDate startDate = LocalDate.of(2026, 7, 6);
        LocalDate endDate = LocalDate.of(2026, 10, 30);

        Optional<AcademicCalendar> existing = academicCalendarRepository.findByActiveTrue().stream()
                .filter(c -> academicYear.equals(c.getAcademicYear())
                        && semester == (c.getSemester() != null ? c.getSemester() : -1)
                        && startDate.equals(c.getStartDate()))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }

        // No specific gazetted holiday dates are given for this window in
        // SOURCE_MATERIAL.md section 5 — seeded with zero holidays rather than
        // inventing any, per the task's explicit instruction.
        AcademicCalendar calendar = AcademicCalendar.builder()
                .academicYear(academicYear)
                .semester(semester)
                .startDate(startDate)
                .endDate(endDate)
                .SaturdayRule("ALL_HOLIDAY")
                .SundayRule("ALL_HOLIDAY")
                .active(true)
                .build();
        return academicCalendarRepository.save(calendar);
    }

    private void seedOopJavaEndToEndFixtures(AcademicCalendar calendar) {
        Optional<Subject> oopJavaOpt = subjectRepository.findBySubjectCode("2321CSC304R");
        if (oopJavaOpt.isEmpty()) {
            return;
        }
        Subject oopJava = oopJavaOpt.get();

        Faculty faculty = facultyRepository.findByEmployeeId("CSE-LP-001").orElseGet(() ->
                facultyRepository.save(Faculty.builder()
                        .employeeId("CSE-LP-001")
                        .name("Sample Faculty")
                        .email("faculty@odysseus.edu") // matches the seeded "faculty" User for /my-plans demo
                        .department("CSE")
                        .maxWeeklyLoad(18)
                        .active(true)
                        .build()));

        // Ensure faculty has eligibility for the seeded subjects
        boolean facultyUpdated = false;
        if (!faculty.getEligibleSubjects().contains(oopJava)) {
            faculty.getEligibleSubjects().add(oopJava);
            facultyUpdated = true;
        }
        Optional<Subject> aimlOpt = subjectRepository.findBySubjectCode("2321CSC303J");
        if (aimlOpt.isPresent() && !faculty.getEligibleSubjects().contains(aimlOpt.get())) {
            faculty.getEligibleSubjects().add(aimlOpt.get());
            facultyUpdated = true;
        }
        if (facultyUpdated) {
            faculty = facultyRepository.save(faculty);
        }

        Batch batch = batchRepository.findByActiveTrue().stream()
                .filter(b -> "CSE-3A".equals(b.getBatchName()) && "CSE".equals(b.getDepartment()))
                .findFirst()
                .orElseGet(() -> batchRepository.save(Batch.builder()
                        .batchName("CSE-3A")
                        .section("A")
                        .department("CSE")
                        .semester(3)
                        .strength(60)
                        .academicYear("2026-2027")
                        .active(true)
                        .build()));

        if (!subjectOfferingRepository.existsByBatchIdAndSubjectIdAndActiveTrue(batch.getId(), oopJava.getId())) {
            subjectOfferingRepository.save(SubjectOffering.builder()
                    .batch(batch)
                    .subject(oopJava)
                    .assignedFaculty(faculty)
                    .academicYear("2026-2027")
                    .semester(3)
                    .active(true)
                    .build());
        }

        if (aimlOpt.isPresent() && !subjectOfferingRepository.existsByBatchIdAndSubjectIdAndActiveTrue(batch.getId(), aimlOpt.get().getId())) {
            subjectOfferingRepository.save(SubjectOffering.builder()
                    .batch(batch)
                    .subject(aimlOpt.get())
                    .assignedFaculty(faculty)
                    .academicYear("2026-2027")
                    .semester(3)
                    .active(true)
                    .build());
        }
    }

    // ---------------------------------------------------------- seed-file DTOs
    // Mirrors docs/lesson-plan/seed-syllabus-data.json exactly; used only for
    // deserialization, not exposed via the REST API.

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SeedFile {
        public List<SeedSubject> subjects;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SeedSubject {
        public String subjectCode;
        public String subjectName;
        public Integer semester;
        public String category;
        public String courseType;
        public Map<String, Integer> periodsPerWeek;
        public Double credits;
        public List<SeedObjective> objectives;
        public List<SeedOutcome> outcomes;
        public Map<String, List<SeedUnit>> units;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SeedObjective {
        public Integer sequenceNumber;
        public String description;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SeedOutcome {
        public Integer coNumber;
        public String description;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SeedUnit {
        public Integer unitNumber;
        public String title;
        public List<String> topics;
        public Integer periodsAllotted;
        public boolean periodsAllottedIsDerived;
        public String derivationNote;
    }
}
