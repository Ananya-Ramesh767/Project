package com.odysseus.backend.controller;

import com.odysseus.backend.domain.*;
import com.odysseus.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/list")
    public ResponseEntity<List<Map<String, Object>>> getTeachingFacultyList() {
        List<Faculty> list = facultyRepository.findByActiveTrue().stream()
                .filter(Faculty::isEligibleForTeaching)
                .sorted(Comparator.comparing(Faculty::getName))
                .collect(Collectors.toList());

        List<Map<String, Object>> result = list.stream().map(f -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", f.getId());
            m.put("name", f.getName());
            m.put("employeeId", f.getEmployeeId());
            m.put("department", f.getDepartment());
            m.put("departmentKey", f.getDepartmentKey());
            m.put("designation", f.getDesignation());
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getFacultyProfile(@RequestParam Long facultyId) {
        Faculty faculty = facultyRepository.findById(facultyId).orElse(null);
        if (faculty == null) {
            return ResponseEntity.notFound().build();
        }

        List<SubjectOffering> offerings = offeringRepository.findByAssignedFacultyIdAndActiveTrue(facultyId);
        int totalWeeklyHours = 0;
        for (SubjectOffering o : offerings) {
            Subject s = o.getSubject();
            if (s != null) {
                int l = (s.getWeeklyLecturePeriods() != null) ? s.getWeeklyLecturePeriods() : 3;
                int p = (s.getWeeklyPracticalPeriods() != null) ? s.getWeeklyPracticalPeriods() : 0;
                totalWeeklyHours += (l + p);
            }
        }

        Map<String, Object> res = new HashMap<>();
        res.put("id", faculty.getId());
        res.put("name", faculty.getName());
        res.put("employeeId", faculty.getEmployeeId());
        res.put("email", faculty.getEmail());
        res.put("department", faculty.getDepartment());
        res.put("departmentKey", faculty.getDepartmentKey());
        res.put("designation", faculty.getDesignation());
        res.put("qualification", faculty.getQualification());
        res.put("specialization", faculty.getSpecialization());
        res.put("category", faculty.getCategory());
        res.put("eligibleForTeaching", faculty.isEligibleForTeaching());
        res.put("maxWeeklyLoad", faculty.getMaxWeeklyLoad() != null ? faculty.getMaxWeeklyLoad() : 18);
        res.put("currentLoad", totalWeeklyHours);
        res.put("totalSubjects", offerings.size());

        return ResponseEntity.ok(res);
    }

    @GetMapping("/my-subjects")
    public ResponseEntity<List<Map<String, Object>>> getMySubjects(@RequestParam Long facultyId) {
        List<SubjectOffering> offerings = offeringRepository.findByAssignedFacultyIdAndActiveTrue(facultyId);

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
    public ResponseEntity<List<Map<String, Object>>> getMyTimetable(@RequestParam Long facultyId) {
        List<TimetableAssignment> assignments = timetableRepository.findByFacultyIdAndActiveTrue(facultyId);

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
    public ResponseEntity<?> getMyWorkload(@RequestParam Long facultyId) {
        List<SubjectOffering> offerings = offeringRepository.findByAssignedFacultyIdAndActiveTrue(facultyId);
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
        int maxHours = 18;
        int freePeriods = Math.max(0, (6 * 8) - totalHours);

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
