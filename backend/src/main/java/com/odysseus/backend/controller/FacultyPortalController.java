package com.odysseus.backend.controller;

import com.odysseus.backend.domain.*;
import com.odysseus.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/faculty")
@RequiredArgsConstructor
@Slf4j
public class FacultyPortalController {

    private final FacultyRepository facultyRepository;
    private final SubjectOfferingRepository offeringRepository;
    private final TimetableAssignmentRepository timetableRepository;
    private final LessonPlanRepository lessonPlanRepository;
    private final LessonPlanSessionRepository lessonPlanSessionRepository;
    private final UserRepository userRepository;

    private static String normalizeEmail(String email) {
        if (email == null) return "";
        return email.trim().toLowerCase().replaceAll("\\.+", ".").replaceAll("\\.@|@\\.", "@");
    }

    private static String cleanName(String name) {
        if (name == null) return "";
        return name.toLowerCase()
                .replaceAll("\\b(dr|mr|mrs|ms|prof|asst|assoc)\\b", "")
                .replaceAll("[^a-z0-9]", "");
    }

    /**
     * Resolves the authenticated faculty member strictly from JWT authentication context.
     * Looks up by user email against faculty email with robust normalization (handling double dots,
     * subdomains, and name matching fallbacks).
     */
    private Faculty resolveAuthenticatedFaculty() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .or(() -> userRepository.findByEmail(username))
                .orElse(null);
        if (user == null) {
            return null;
        }

        List<Faculty> activeFaculty = facultyRepository.findByActiveTrue();
        String userEmail = user.getEmail() != null ? user.getEmail() : "";
        String normUserEmail = normalizeEmail(userEmail);
        String userPrefix = normUserEmail.contains("@") ? normUserEmail.substring(0, normUserEmail.indexOf('@')) : normUserEmail;
        String userCleanName = cleanName(user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getUsername());

        // 1. Exact match on raw email
        if (!userEmail.isBlank()) {
            for (Faculty f : activeFaculty) {
                if (f.getEmail() != null && f.getEmail().equalsIgnoreCase(userEmail.trim())) {
                    return f;
                }
            }
        }

        // 2. Normalized email match (collapses duplicate dots like "s..bhuvaneswari" -> "s.bhuvaneswari")
        if (!normUserEmail.isBlank()) {
            for (Faculty f : activeFaculty) {
                if (f.getEmail() != null && normalizeEmail(f.getEmail()).equals(normUserEmail)) {
                    return f;
                }
            }
        }

        // 3. Email username prefix match (e.g. s.bhuvaneswari vs s.bhuvaneswari@eec.srmrmp.edu.in)
        if (!userPrefix.isBlank()) {
            for (Faculty f : activeFaculty) {
                if (f.getEmail() != null) {
                    String fNorm = normalizeEmail(f.getEmail());
                    String fPrefix = fNorm.contains("@") ? fNorm.substring(0, fNorm.indexOf('@')) : fNorm;
                    if (fPrefix.equalsIgnoreCase(userPrefix)) {
                        return f;
                    }
                }
            }
        }

        // 4. Cleaned Name match (e.g. user fullName "Bhuvaneswari S" -> "bhuvaneswaris" matches "Ms. S. Bhuvaneswari" -> "sbhuvaneswari" or contains)
        if (!userCleanName.isBlank()) {
            for (Faculty f : activeFaculty) {
                String fClean = cleanName(f.getName());
                if (fClean.equals(userCleanName) ||
                        (fClean.length() > 5 && (userCleanName.contains(fClean) || fClean.contains(userCleanName)))) {
                    return f;
                }
            }
        }

        // 5. Username match (e.g. username is employeeId FAC-CSE-012)
        for (Faculty f : activeFaculty) {
            if (f.getEmployeeId() != null && f.getEmployeeId().equalsIgnoreCase(user.getUsername().trim())) {
                return f;
            }
        }

        return null;
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile() {
        Faculty faculty = resolveAuthenticatedFaculty();
        if (faculty == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No matching Faculty profile found for your account email. Please contact the administrator."));
        }

        List<SubjectOffering> offerings = offeringRepository.findByAssignedFacultyIdAndActiveTrue(faculty.getId());
        int theoryHours = 0;
        int labHours = 0;
        for (SubjectOffering o : offerings) {
            Subject s = o.getSubject();
            if (s != null) {
                int l = (s.getWeeklyLecturePeriods() != null) ? s.getWeeklyLecturePeriods() : 3;
                int p = (s.getWeeklyPracticalPeriods() != null) ? s.getWeeklyPracticalPeriods() : 0;
                theoryHours += l;
                labHours += p;
            }
        }
        int totalHours = theoryHours + labHours;
        int maxHours = faculty.getMaxWeeklyLoad() != null && faculty.getMaxWeeklyLoad() > 0 ? faculty.getMaxWeeklyLoad() : 18;

        // Lesson progress summary
        List<LessonPlan> plans = lessonPlanRepository.findBySubjectOffering_AssignedFaculty_IdAndActiveTrue(faculty.getId());
        int totalSessions = 0;
        int completedSessions = 0;
        for (LessonPlan p : plans) {
            List<LessonPlanSession> sessions = lessonPlanSessionRepository.findByLessonPlanIdAndActiveTrueOrderByOverallSequenceAsc(p.getId());
            totalSessions += sessions.size();
            completedSessions += sessions.stream().filter(s -> "COMPLETED".equalsIgnoreCase(s.getStatus())).count();
        }
        int overallProgress = totalSessions > 0 ? Math.round(((float) completedSessions / totalSessions) * 100) : 0;

        Map<String, Object> res = new HashMap<>();
        res.put("id", faculty.getId());
        res.put("name", faculty.getName());
        res.put("employeeId", faculty.getEmployeeId());
        res.put("email", faculty.getEmail());
        res.put("department", faculty.getDepartment());
        res.put("departmentKey", faculty.getDepartmentKey());
        res.put("designation", faculty.getDesignation());
        res.put("qualification", faculty.getQualification());
        res.put("maxWeeklyLoad", maxHours);
        res.put("currentLoad", totalHours);
        res.put("theoryHours", theoryHours);
        res.put("labHours", labHours);
        res.put("totalSubjects", offerings.size());
        res.put("totalSessions", totalSessions);
        res.put("completedSessions", completedSessions);
        res.put("lessonProgressPercentage", overallProgress);

        return ResponseEntity.ok(res);
    }

    @GetMapping("/my-subjects")
    public ResponseEntity<?> getMySubjects() {
        Faculty faculty = resolveAuthenticatedFaculty();
        if (faculty == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No matching Faculty profile found for your account email."));
        }

        List<SubjectOffering> offerings = offeringRepository.findByAssignedFacultyIdAndActiveTrue(faculty.getId());

        List<Map<String, Object>> list = new ArrayList<>();
        for (SubjectOffering o : offerings) {
            Map<String, Object> m = new HashMap<>();
            Subject s = o.getSubject();
            int l = (s != null && s.getWeeklyLecturePeriods() != null) ? s.getWeeklyLecturePeriods() : 3;
            int p = (s != null && s.getWeeklyPracticalPeriods() != null) ? s.getWeeklyPracticalPeriods() : 0;

            m.put("offeringId", o.getId());
            m.put("batchId", o.getBatch() != null ? o.getBatch().getId() : null);
            m.put("batchName", o.getBatch() != null ? o.getBatch().getBatchName() : "N/A");
            m.put("section", o.getBatch() != null ? o.getBatch().getSection() : "N/A");
            m.put("semester", o.getSemester() != null ? o.getSemester() : (o.getBatch() != null ? o.getBatch().getSemester() : 3));
            m.put("subjectId", s != null ? s.getId() : null);
            m.put("subjectCode", s != null ? s.getSubjectCode() : "N/A");
            m.put("subjectName", s != null ? s.getSubjectName() : "N/A");
            m.put("type", s != null && s.getSubjectType() != null ? s.getSubjectType() : "THEORY");
            m.put("weeklyLecturePeriods", l);
            m.put("weeklyPracticalPeriods", p);
            m.put("academicYear", o.getAcademicYear());
            list.add(m);
        }

        return ResponseEntity.ok(list);
    }

    @GetMapping("/my-timetable")
    public ResponseEntity<?> getMyTimetable() {
        Faculty faculty = resolveAuthenticatedFaculty();
        if (faculty == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No matching Faculty profile found for your account email."));
        }

        List<TimetableAssignment> assignments = timetableRepository.findByFacultyIdAndActiveTrue(faculty.getId());

        List<Map<String, Object>> list = new ArrayList<>();
        for (TimetableAssignment a : assignments) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("dayOfWeek", a.getDayOfWeek());
            m.put("periodNumber", a.getPeriodNumber());
            m.put("durationPeriods", a.getDurationPeriods());
            m.put("isLabBlock", a.isLabBlock());
            m.put("subjectCode", a.getSubject() != null ? a.getSubject().getSubjectCode() : "");
            m.put("subjectName", a.getSubject() != null ? a.getSubject().getSubjectName() : "");
            m.put("subjectType", a.getSubject() != null && a.getSubject().getSubjectType() != null ? a.getSubject().getSubjectType() : "THEORY");
            m.put("batchName", a.getBatch() != null ? a.getBatch().getBatchName() : "");
            m.put("roomNumber", a.getRoom() != null ? a.getRoom().getRoomCode() : "TBA");
            list.add(m);
        }

        return ResponseEntity.ok(list);
    }

    @GetMapping("/my-workload")
    public ResponseEntity<?> getMyWorkload() {
        Faculty faculty = resolveAuthenticatedFaculty();
        if (faculty == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "No matching Faculty profile found for your account email."));
        }

        List<SubjectOffering> offerings = offeringRepository.findByAssignedFacultyIdAndActiveTrue(faculty.getId());
        int theoryHours = 0;
        int labHours = 0;

        for (SubjectOffering o : offerings) {
            Subject s = o.getSubject();
            if (s != null) {
                int l = (s.getWeeklyLecturePeriods() != null) ? s.getWeeklyLecturePeriods() : 3;
                int p = (s.getWeeklyPracticalPeriods() != null) ? s.getWeeklyPracticalPeriods() : 0;
                if (p > 0) {
                    labHours += p;
                }
                theoryHours += (l > 0 ? l : 3);
            }
        }
        int totalHours = theoryHours + labHours;
        int maxHours = faculty.getMaxWeeklyLoad() != null && faculty.getMaxWeeklyLoad() > 0 ? faculty.getMaxWeeklyLoad() : 18;
        int freePeriods = Math.max(0, (5 * 8) - totalHours);

        Map<String, Object> wl = new HashMap<>();
        wl.put("totalAssignedHours", totalHours);
        wl.put("theoryHours", theoryHours);
        wl.put("labHours", labHours);
        wl.put("maxHours", maxHours);
        wl.put("remainingCapacity", Math.max(0, maxHours - totalHours));
        wl.put("freePeriodsEst", freePeriods);
        wl.put("isOverload", totalHours > maxHours);

        return ResponseEntity.ok(wl);
    }
}
