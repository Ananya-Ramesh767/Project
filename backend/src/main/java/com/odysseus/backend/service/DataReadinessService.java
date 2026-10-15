package com.odysseus.backend.service;

import com.odysseus.backend.domain.*;
import com.odysseus.backend.dto.DataReadinessResult;
import com.odysseus.backend.repository.*;
import com.odysseus.backend.util.DepartmentMatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Scope-aware Data Readiness Service.
 *
 * Validates ONLY the data required for timetable generation within the
 * specified scheduling scope (department + semester + academicYear).
 *
 * KEY DESIGN DECISIONS:
 * 1. Faculty Master contains ALL faculty — not every faculty needs a subject assignment.
 * 2. Only faculty that are assigned to active offerings in the scope are evaluated.
 * 3. The question is: "Is the scope's data sufficient to generate a valid timetable?"
 * 4. BLOCKING issues prevent generation. WARNINGS are advisory only.
 */
@Service
@RequiredArgsConstructor
public class DataReadinessService {

    private final SubjectRepository subjectRepository;
    private final FacultyRepository facultyRepository;
    private final RoomRepository roomRepository;
    private final BatchRepository batchRepository;
    private final SubjectOfferingRepository offeringRepository;
    private final AcademicCalendarRepository calendarRepository;
    private final CollegeTimeslotRepository timeslotRepository;
    private final NepCreditRuleRepository nepRuleRepository;

    /**
     * Global readiness check (no scope — used by scheduler pre-generation validation).
     * Only checks structural integrity, not faculty assignment completeness.
     */
    public DataReadinessResult checkDataReadiness() {
        return checkDataReadiness(null, null, null);
    }

    /**
     * Scope-aware readiness check.
     *
     * @param department  e.g. "Computer Science Engineering" — null means all departments
     * @param semester    e.g. 3 — null means all semesters
     * @param academicYear e.g. "2026-2027" — null means current active
     */
    public DataReadinessResult checkDataReadiness(String department, Integer semester, String academicYear) {

        List<String> blockingIssues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        boolean isScoped = (department != null && !department.isBlank()) || semester != null;

        // ── 1. NEP Credit Rules ────────────────────────────────────────────────
        List<NepCreditRule> activeRules = nepRuleRepository.findByActiveTrue();
        if (activeRules.isEmpty()) {
            blockingIssues.add("No active NEP Credit Rule configured. Configure credit-to-hours conversion rules in NEP Rules.");
        }

        // ── 2. Timeslots ───────────────────────────────────────────────────────
        List<CollegeTimeslot> activeTimeslots = timeslotRepository.findByActiveTrue();
        long teachingSlots = activeTimeslots.stream().filter(t -> !t.isBreak() && !t.isLunch()).count();
        if (teachingSlots == 0) {
            blockingIssues.add("No active teaching timeslots configured. Configure the college period schedule in Timeslots.");
        }

        // ── 3. Academic Calendar ───────────────────────────────────────────────
        List<AcademicCalendar> activeCalendars = calendarRepository.findByActiveTrue();
        if (activeCalendars.isEmpty()) {
            blockingIssues.add("No active academic calendar configured. Set up the academic year calendar.");
        }

        // ── 4. Rooms ───────────────────────────────────────────────────────────
        List<Room> activeRooms = roomRepository.findByActiveTrue();
        if (activeRooms.isEmpty()) {
            blockingIssues.add("No active rooms configured. Add lecture halls and labs in Room Master.");
        } else {
            boolean hasLectureHall = activeRooms.stream().anyMatch(r -> !isLabRoom(r.getRoomType()));
            if (!hasLectureHall) {
                blockingIssues.add("No theory/lecture rooms configured. Theory classes require at least one lecture hall or classroom.");
            }
            for (Room r : activeRooms) {
                if (r.getCapacity() == null || r.getCapacity() <= 0) {
                    warnings.add("Room [" + r.getRoomCode() + "] has no capacity configured.");
                }
            }
        }

        // ── 5. Subjects (scoped) ───────────────────────────────────────────────
        List<Subject> scopedSubjects = subjectRepository.findByActiveTrue().stream()
                .filter(s -> !isScoped || matchesDeptAndSem(s.getDepartment(), s.getSemester(), department, semester))
                .collect(Collectors.toList());

        if (scopedSubjects.isEmpty()) {
            if (isScoped) {
                blockingIssues.add("No active subjects found for the selected scope"
                        + (department != null ? " [" + shortDept(department) + "]" : "")
                        + (semester != null ? " Semester " + semester : "")
                        + ". Configure subjects in Subjects & Credits.");
            } else {
                blockingIssues.add("No active subjects found in the system. Configure subjects in Subjects & Credits.");
            }
        } else {
            List<Room> labRooms = activeRooms.stream().filter(r -> isLabRoom(r.getRoomType())).collect(Collectors.toList());
            for (Subject s : scopedSubjects) {
                if (s.getCalculatedWeeklyHours() <= 0 && s.getWeeklyLecturePeriods() == null
                        && s.getWeeklyPracticalPeriods() == null) {
                    warnings.add("Subject [" + s.getSubjectCode() + " \u2013 " + s.getSubjectName()
                            + "] has zero calculated weekly hours. Verify credit configuration.");
                }
                // Check lab subjects (by subjectType OR requiresLab flag)
                boolean isLabSubject = "LAB".equalsIgnoreCase(s.getSubjectType())
                        || "THEORY_AND_LAB".equalsIgnoreCase(s.getSubjectType())
                        || s.isRequiresLab();
                if (isLabSubject) {
                    if (s.isRequiresLab() && (s.getLabDuration() == null || s.getLabDuration() < 2)) {
                        blockingIssues.add("Subject [" + s.getSubjectCode() + "] requires a lab but lab duration is not set (must be \u2265 2 continuous periods).");
                    }
                    if (labRooms.isEmpty()) {
                        blockingIssues.add("Subject [" + s.getSubjectCode() + " \u2013 " + s.getSubjectName()
                                + "] is a LAB subject but no active LAB rooms are configured in Room Master. "
                                + "Add rooms with type LAB in Room Master.");
                    }
                }
            }
        }

        // ── 6. Batches/Sections (scoped) ───────────────────────────────────────
        List<Batch> scopedBatches = batchRepository.findByActiveTrue().stream()
                .filter(b -> !isScoped || matchesDeptAndSem(b.getDepartment(), b.getSemester(), department, semester))
                .collect(Collectors.toList());

        if (scopedBatches.isEmpty()) {
            if (isScoped) {
                blockingIssues.add("No active batches/sections found for the selected scope. Configure batches in Batches & Sections.");
            } else {
                blockingIssues.add("No active student batches/sections found. Configure sections in Batches & Sections.");
            }
        } else {
            long lectureHallCount = activeRooms.stream().filter(r -> !isLabRoom(r.getRoomType())).count();
            if (lectureHallCount < scopedBatches.size()) {
                blockingIssues.add("Insufficient classrooms: " + scopedBatches.size() + " section(s) in scope require dedicated classrooms, but only "
                        + lectureHallCount + " lecture hall(s) are configured in Room Master. Add more classrooms.");
            }

            for (Batch b : scopedBatches) {
                if (b.getStrength() == null || b.getStrength() <= 0) {
                    warnings.add("Batch [" + b.getBatchName() + "] has no student strength configured.");
                } else if (b.getAssignedRoomCode() != null && !b.getAssignedRoomCode().isBlank()) {
                    Room assignedRoom = activeRooms.stream()
                            .filter(r -> r.getRoomCode().equalsIgnoreCase(b.getAssignedRoomCode()))
                            .findFirst().orElse(null);
                    if (assignedRoom != null && assignedRoom.getCapacity() != null && assignedRoom.getCapacity() < b.getStrength()) {
                        warnings.add("Section [" + b.getBatchName() + "] has student strength (" + b.getStrength()
                                + ") exceeding assigned room [" + assignedRoom.getRoomCode() + "] capacity (" + assignedRoom.getCapacity() + ").");
                    }
                }
            }
        }

        // ── 7. Subject Offerings (CORE SCOPE VALIDATION) ──────────────────────
        // This is the most important check: for each batch×subject combination in scope,
        // does an offering exist, and does it have a faculty assigned?
        List<SubjectOffering> allActiveOfferings = offeringRepository.findByActiveTrue();

        // Filter offerings to scope
        List<SubjectOffering> scopedOfferings = allActiveOfferings.stream()
                .filter(o -> {
                    if (!isScoped) return true;
                    boolean deptMatch = department == null || department.isBlank()
                            || DepartmentMatcher.matches(o.getBatch().getDepartment(), department)
                            || DepartmentMatcher.matches(o.getSubject().getDepartment(), department);
                    boolean semMatch = semester == null
                            || matchesSemester(o, semester);
                    return deptMatch && semMatch;
                })
                .collect(Collectors.toList());

        if (scopedOfferings.isEmpty() && !scopedBatches.isEmpty() && !scopedSubjects.isEmpty()) {
            blockingIssues.add("No subject offerings exist for the selected scope. Use Subject Offerings to assign subjects to sections.");
        } else {
            // Check each scoped batch has at least one offering
            for (Batch b : scopedBatches) {
                boolean hasOffering = scopedOfferings.stream()
                        .anyMatch(o -> o.getBatch().getId().equals(b.getId()));
                if (!hasOffering) {
                    blockingIssues.add("Section [" + b.getBatchName() + "] has no subject offerings assigned.");
                }
            }

            // Check each offering has a faculty assigned (CRITICAL for generation)
            for (SubjectOffering o : scopedOfferings) {
                if (o.getAssignedFaculty() == null) {
                    blockingIssues.add("No faculty assigned for: [" + o.getSubject().getSubjectCode()
                            + " – " + o.getSubject().getSubjectName()
                            + "] → Section [" + o.getBatch().getBatchName() + "]. Assign faculty in Subject Offerings.");
                } else {
                    // Verify the assigned faculty is eligible
                    Faculty f = o.getAssignedFaculty();
                    Subject s = o.getSubject();
                    boolean isEligible = f.getEligibleSubjects().stream()
                            .anyMatch(es -> es.getId().equals(s.getId()));
                    if (!isEligible && !"SPECIAL".equalsIgnoreCase(s.getSubjectType())) {
                        warnings.add("Faculty [" + f.getName() + "] assigned to [" + s.getSubjectCode()
                                + "] for section [" + o.getBatch().getBatchName()
                                + "] but is not in the eligible subjects list. Verify in Faculty Master.");
                    }
                    // Check faculty workload limit is set
                    if (f.getMaxWeeklyLoad() == null || f.getMaxWeeklyLoad() <= 0) {
                        warnings.add("Faculty [" + f.getName()
                                + "] has no maximum weekly load configured. Default of 18 periods will be used.");
                    }
                }
            }

            // Workload aggregation check: prevent hard infeasibility in solver
            java.util.Map<Long, Integer> facultyLoadMap = new java.util.HashMap<>();
            java.util.Map<Long, Integer> facultySectionCount = new java.util.HashMap<>();
            java.util.Map<Long, Faculty> facultyEntityMap = new java.util.HashMap<>();
            for (SubjectOffering o : scopedOfferings) {
                if (o.getAssignedFaculty() != null && o.getAssignedFaculty().getId() != null) {
                    Faculty f = o.getAssignedFaculty();
                    facultyEntityMap.put(f.getId(), f);
                    int hours = getSubjectWeeklyHours(o.getSubject());
                    facultyLoadMap.merge(f.getId(), hours, Integer::sum);
                    facultySectionCount.merge(f.getId(), 1, Integer::sum);
                }
            }
            for (java.util.Map.Entry<Long, Integer> entry : facultyLoadMap.entrySet()) {
                Long fid = entry.getKey();
                Faculty f = facultyEntityMap.get(fid);
                if (f == null) continue;
                int totalHours = entry.getValue();
                int max = f.getMaxWeeklyLoad() != null && f.getMaxWeeklyLoad() > 0 ? f.getMaxWeeklyLoad() : 18;
                if (totalHours > max) {
                    blockingIssues.add("Faculty [" + f.getName() + "] is overloaded: assigned " + totalHours
                            + " hours across " + facultySectionCount.get(fid) + " section(s), exceeding maximum weekly load of "
                            + max + " hours. Distribute sections in Subject Offerings or run Auto-Assign.");
                }
            }
        }

        // ── 8. Build result ────────────────────────────────────────────────────
        // Combine for legacy 'issues' field (backward compat)
        List<String> allIssues = new ArrayList<>();
        allIssues.addAll(blockingIssues);
        allIssues.addAll(warnings);

        boolean isReady = blockingIssues.isEmpty();

        String scopeDesc = buildScopeDesc(department, semester, academicYear);
        String summary;
        if (isReady) {
            summary = "Data readiness check passed" + scopeDesc + ". "
                    + scopedOfferings.size() + " offerings validated across "
                    + scopedBatches.size() + " sections.";
            if (!warnings.isEmpty()) {
                summary += " " + warnings.size() + " advisory warning(s) noted.";
            }
        } else {
            summary = blockingIssues.size() + " blocking issue(s) found" + scopeDesc
                    + ". Resolve all blocking issues before generating the timetable.";
        }

        return DataReadinessResult.builder()
                .ready(isReady)
                .issueCount(allIssues.size())
                .issues(allIssues)
                .blockingIssues(blockingIssues)
                .warnings(warnings)
                .summaryMessage(summary)
                .department(department)
                .semester(semester != null ? String.valueOf(semester) : null)
                .academicYear(academicYear)
                .subjectsChecked(scopedSubjects.size())
                .offeringsChecked(scopedOfferings.size())
                .batchesChecked(scopedBatches.size())
                .build();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private boolean matchesDeptAndSem(String entityDept, Integer entitySem,
                                       String filterDept, Integer filterSem) {
        boolean deptOk = filterDept == null || filterDept.isBlank()
                || DepartmentMatcher.matches(entityDept, filterDept);
        boolean semOk = filterSem == null || filterSem.equals(entitySem);
        return deptOk && semOk;
    }

    private boolean matchesSemester(SubjectOffering o, Integer semester) {
        if (o.getSemester() != null) return semester.equals(o.getSemester());
        if (o.getBatch().getSemester() != null) return semester.equals(o.getBatch().getSemester());
        if (o.getSubject().getSemester() != null) return semester.equals(o.getSubject().getSemester());
        return true;
    }

    private String buildScopeDesc(String department, Integer semester, String academicYear) {
        if (department == null && semester == null && academicYear == null) return "";
        List<String> parts = new ArrayList<>();
        if (department != null && !department.isBlank()) parts.add(shortDept(department));
        if (semester != null) parts.add("Semester " + semester);
        if (academicYear != null && !academicYear.isBlank()) parts.add(academicYear);
        return " for scope [" + String.join(" / ", parts) + "]";
    }

    private String shortDept(String department) {
        if (department == null) return "";
        // Return abbreviated form for display
        if (department.toLowerCase().contains("computer science") && department.toLowerCase().contains("engineering")) return "CSE";
        if (department.toLowerCase().contains("information technology")) return "IT";
        if (department.toLowerCase().contains("electronics")) return "ECE";
        if (department.toLowerCase().contains("mechanical")) return "MECH";
        if (department.toLowerCase().contains("civil")) return "CIVIL";
        return department.length() > 30 ? department.substring(0, 30) + "…" : department;
    }

    private int getSubjectWeeklyHours(Subject s) {
        if (s == null) return 0;
        int l = s.getWeeklyLecturePeriods() != null ? s.getWeeklyLecturePeriods() : 0;
        int p = s.getWeeklyPracticalPeriods() != null ? s.getWeeklyPracticalPeriods() : 0;
        int t = s.getWeeklyTutorialPeriods() != null ? s.getWeeklyTutorialPeriods() : 0;
        if (l == 0 && p == 0 && t == 0) {
            l = (int) Math.ceil(s.getCalculatedWeeklyHours());
            if (l == 0) l = 1;
        }
        return l + p + t;
    }

    /**
     * Determines if a room type is a laboratory (LAB, LABORATORY, or contains LAB).
     * Consistent with TimetableSolverService.
     */
    private boolean isLabRoom(String roomType) {
        if (roomType == null || roomType.isBlank()) return false;
        String upper = roomType.toUpperCase().trim();
        return upper.equals("LAB") || upper.equals("LABORATORY") || upper.contains("_LAB") || upper.contains("LAB_");
    }
}
