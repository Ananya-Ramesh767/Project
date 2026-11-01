package com.odysseus.backend.controller;

import com.odysseus.backend.domain.*;
import com.odysseus.backend.repository.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/faculty")
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class FacultyController {

    private final FacultyRepository facultyRepository;
    private final SubjectRepository subjectRepository;
    private final FacultyAvailabilityRepository availabilityRepository;
    private final SubjectOfferingRepository offeringRepository;
    private final TimetableAssignmentRepository timetableRepository;
    private final BatchRepository batchRepository;
    private final com.odysseus.backend.service.PdfExtractionService pdfExtractionService;
    private final com.odysseus.backend.service.FacultyAllocationService allocationService;
    private final com.odysseus.backend.service.DepartmentService departmentService;
    private final com.odysseus.backend.service.CourseDepartmentMappingService courseMappingService;

    @GetMapping
    public ResponseEntity<List<Faculty>> getAllFaculty(
            @RequestParam(required = false) String department) {
        List<Faculty> list = facultyRepository.findAll();
        if (department != null && !department.trim().isEmpty() && !department.equalsIgnoreCase("ALL")) {
            list = list.stream()
                    .filter(f -> com.odysseus.backend.util.DepartmentMatcher.matches(f.getDepartment(), department))
                    .collect(Collectors.toList());
        }
        return ResponseEntity.ok(list);
    }

    @GetMapping("/department-counts")
    public ResponseEntity<Map<String, Long>> getDepartmentCounts() {
        List<Faculty> allFaculty = facultyRepository.findByActiveTrue();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Faculty f : allFaculty) {
            String dept = f.getDepartment();
            if (dept != null && !dept.isBlank()) {
                counts.put(dept, counts.getOrDefault(dept, 0L) + 1);
            }
        }
        return ResponseEntity.ok(counts);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Faculty> getFacultyById(@PathVariable Long id) {
        return facultyRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createFaculty(@RequestBody Map<String, Object> payload) {
        String employeeId = (String) payload.get("employeeId");
        if (facultyRepository.existsByEmployeeId(employeeId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Employee ID [" + employeeId + "] already exists."));
        }

        Faculty faculty = Faculty.builder()
                .employeeId(employeeId)
                .name((String) payload.get("name"))
                .email((String) payload.get("email"))
                .department((String) payload.get("department"))
                .maxWeeklyLoad(payload.get("maxWeeklyLoad") != null ? ((Number) payload.get("maxWeeklyLoad")).intValue() : 18)
                .active(payload.get("active") != null ? (Boolean) payload.get("active") : true)
                .build();

        if (payload.containsKey("subjectIds")) {
            @SuppressWarnings("unchecked")
            List<Number> subIds = (List<Number>) payload.get("subjectIds");
            Set<Subject> subjects = new HashSet<>();
            for (Number sId : subIds) {
                subjectRepository.findById(sId.longValue()).ifPresent(subjects::add);
            }
            faculty.setEligibleSubjects(subjects);
        }

        Faculty saved = facultyRepository.save(faculty);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateFaculty(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return facultyRepository.findById(id).map(faculty -> {
            String employeeId = (String) payload.get("employeeId");
            if (facultyRepository.existsByEmployeeIdAndIdNot(employeeId, id)) {
                return ResponseEntity.badRequest().body(Map.of("error", "Employee ID [" + employeeId + "] is taken by another faculty member."));
            }

            faculty.setEmployeeId(employeeId);
            faculty.setName((String) payload.get("name"));
            faculty.setEmail((String) payload.get("email"));
            faculty.setDepartment((String) payload.get("department"));
            if (payload.get("maxWeeklyLoad") != null) {
                faculty.setMaxWeeklyLoad(((Number) payload.get("maxWeeklyLoad")).intValue());
            }
            if (payload.get("active") != null) {
                faculty.setActive((Boolean) payload.get("active"));
            }

            if (payload.containsKey("subjectIds")) {
                @SuppressWarnings("unchecked")
                List<Number> subIds = (List<Number>) payload.get("subjectIds");
                Set<Subject> subjects = new HashSet<>();
                for (Number sId : subIds) {
                    subjectRepository.findById(sId.longValue()).ifPresent(subjects::add);
                }
                faculty.setEligibleSubjects(subjects);
            }

            Faculty saved = facultyRepository.save(faculty);
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFaculty(@PathVariable Long id) {
        return facultyRepository.findById(id).map(f -> {
            f.setActive(false);
            facultyRepository.save(f);
            return ResponseEntity.ok(Map.of("message", "Faculty deactivated successfully"));
        }).orElse(ResponseEntity.notFound().build());
    }

    // --- Faculty Availability Endpoints ---

    @GetMapping("/{id}/availability")
    public ResponseEntity<List<FacultyAvailability>> getFacultyAvailability(@PathVariable Long id) {
        return ResponseEntity.ok(availabilityRepository.findByFacultyId(id));
    }

    @Data
    public static class AvailabilityItem {
        private String dayOfWeek;
        private int periodNumber;
        private boolean available;
        private boolean preferred;
    }

    @PutMapping("/{id}/availability")
    @Transactional
    public ResponseEntity<?> updateFacultyAvailability(@PathVariable Long id, @RequestBody List<AvailabilityItem> items) {
        Faculty faculty = facultyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Faculty not found"));

        availabilityRepository.deleteByFacultyId(id);

        List<FacultyAvailability> savedList = new ArrayList<>();
        for (AvailabilityItem item : items) {
            FacultyAvailability fa = FacultyAvailability.builder()
                    .faculty(faculty)
                    .dayOfWeek(item.getDayOfWeek().toUpperCase())
                    .periodNumber(item.getPeriodNumber())
                    .available(item.isAvailable())
                    .preferred(item.isPreferred())
                    .build();
            savedList.add(availabilityRepository.save(fa));
        }

        return ResponseEntity.ok(Map.of(
                "message", "Availability updated successfully",
                "count", savedList.size()
        ));
    }

    // --- Faculty Workload & Pool Metrics ---

    @GetMapping("/workload")
    public ResponseEntity<List<Map<String, Object>>> getFacultyWorkloads() {
        List<Faculty> allFaculty = facultyRepository.findByActiveTrue();
        List<TimetableAssignment> assignments = timetableRepository.findByActiveTrue();
        List<SubjectOffering> offerings = offeringRepository.findByActiveTrue();

        List<Map<String, Object>> result = new ArrayList<>();

        for (Faculty f : allFaculty) {
            int scheduledPeriods = assignments.stream()
                    .filter(a -> a.getFaculty().getId().equals(f.getId()))
                    .mapToInt(a -> a.getDurationPeriods() != null ? a.getDurationPeriods() : 1)
                    .sum();

            List<SubjectOffering> assignedOfferings = offerings.stream()
                    .filter(o -> o.getAssignedFaculty() != null && o.getAssignedFaculty().getId().equals(f.getId()))
                    .toList();

            int maxLoad = f.getMaxWeeklyLoad() != null ? f.getMaxWeeklyLoad() : 18;
            int remainingCapacity = Math.max(0, maxLoad - scheduledPeriods);

            Map<String, Object> map = new HashMap<>();
            map.put("facultyId", f.getId());
            map.put("employeeId", f.getEmployeeId());
            map.put("name", f.getName());
            map.put("department", f.getDepartment());
            map.put("maxWeeklyLoad", maxLoad);
            map.put("assignedPeriods", scheduledPeriods);
            map.put("remainingCapacity", remainingCapacity);
            map.put("assignedOfferingsCount", assignedOfferings.size());

            result.add(map);
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/pool/{subjectId}")
    public ResponseEntity<?> getEligibleFacultyPool(@PathVariable Long subjectId) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found"));

        List<Faculty> allFaculty = facultyRepository.findByActiveTrue();
        List<TimetableAssignment> assignments = timetableRepository.findByActiveTrue();

        List<Map<String, Object>> pool = new ArrayList<>();
        for (Faculty f : allFaculty) {
            boolean isEligible = f.getEligibleSubjects().contains(subject);
            if (!isEligible) continue;

            int scheduledPeriods = assignments.stream()
                    .filter(a -> a.getFaculty().getId().equals(f.getId()))
                    .mapToInt(a -> a.getDurationPeriods() != null ? a.getDurationPeriods() : 1)
                    .sum();

            int maxLoad = f.getMaxWeeklyLoad() != null ? f.getMaxWeeklyLoad() : 18;

            Map<String, Object> facMap = new HashMap<>();
            facMap.put("facultyId", f.getId());
            facMap.put("name", f.getName());
            facMap.put("employeeId", f.getEmployeeId());
            facMap.put("email", f.getEmail());
            facMap.put("currentLoad", scheduledPeriods);
            facMap.put("maxLoad", maxLoad);
            facMap.put("availableCapacity", Math.max(0, maxLoad - scheduledPeriods));
            pool.add(facMap);
        }

        return ResponseEntity.ok(pool);
    }

    // ------------------------------------------------------------------------------------
    // HELPER: Dynamic check whether faculty specialization or subjects handled matches subject
    // ------------------------------------------------------------------------------------
    private boolean matchesSpecializationOrSubjects(Faculty f, Subject subject) {
        if (f == null || subject == null) return false;
        String spec = f.getSpecialization();
        String subjHandled = f.getSubjectsHandled();
        String combined = ((spec != null ? spec : "") + " " + (subjHandled != null ? subjHandled : "")).toLowerCase();
        if (combined.trim().isEmpty()) return false;

        // 1. Direct code match (e.g. "CS301", "2321CSC302J")
        String code = subject.getSubjectCode() != null ? subject.getSubjectCode().toLowerCase().trim() : "";
        if (!code.isEmpty() && combined.contains(code)) return true;

        // 2. Direct full name match
        String name = subject.getSubjectName() != null ? subject.getSubjectName().toLowerCase().trim() : "";
        if (!name.isEmpty() && combined.contains(name)) return true;

        // 3. Significant keywords match (length >= 4, ignoring common non-discriminating terms)
        String[] words = name.split("[^a-zA-Z0-9]+");
        Set<String> stopWords = Set.of("intro", "introduction", "fundamentals", "applied", "engineering",
                "science", "theory", "laboratory", "advanced", "course", "basics", "studies", "principles");
        for (String w : words) {
            if (w.length() >= 4 && !stopWords.contains(w) && combined.contains(w)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------------------------
    // PROPOSE ASSIGNMENT: Returns subject → {eligible faculty, suggested faculty} for admin review.
    // Dynamic matching by specialization, subjects handled, or existing eligibility.
    // ------------------------------------------------------------------------------------
    @GetMapping("/propose-assignment")
    public ResponseEntity<?> proposeAssignment(
            @RequestParam(required = false, defaultValue = "") String department,
            @RequestParam(required = false) String semester) {

        // Determine semester filter
        Integer targetSemester = null;
        Boolean isOdd = null;
        if (semester != null && !semester.trim().isEmpty()) {
            String semClean = semester.trim();
            if (semClean.equalsIgnoreCase("Odd") || semClean.equalsIgnoreCase("Odd Semester")) {
                isOdd = true;
            } else if (semClean.equalsIgnoreCase("Even") || semClean.equalsIgnoreCase("Even Semester")) {
                isOdd = false;
            } else {
                String digits = semClean.replaceAll("[^0-9]", "");
                if (!digits.isEmpty()) {
                    try {
                        targetSemester = Integer.parseInt(digits);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        final Integer finalTargetSemester = targetSemester;
        final Boolean finalIsOdd = isOdd;

        // Treat "ALL" as wildcard (no department filtering)
        boolean allDept = department == null || department.isBlank() || department.equalsIgnoreCase("ALL");
        String deptFilter = allDept ? "" : department;

        // Get subjects for this department (or all if wildcard)
        List<Subject> subjects = subjectRepository.findAll().stream()
                .filter(Subject::isActive)
                .filter(s -> deptFilter.isEmpty() || com.odysseus.backend.util.DepartmentMatcher.matches(s.getDepartment(), deptFilter))
                .filter(s -> {
                    if (finalTargetSemester != null) {
                        return Objects.equals(s.getSemester(), finalTargetSemester);
                    }
                    if (finalIsOdd != null && s.getSemester() != null) {
                        return finalIsOdd ? (s.getSemester() % 2 == 1) : (s.getSemester() % 2 == 0);
                    }
                    return true;
                })
                .sorted(Comparator.comparing(Subject::getSubjectCode))
                .collect(Collectors.toList());

        // Get all active faculty
        List<Faculty> allActiveFaculty = facultyRepository.findByActiveTrue();

        // Active batches for section assignment (filtered by department if not wildcard)
        List<Batch> batches = batchRepository.findAll().stream()
                .filter(Batch::isActive)
                .filter(b -> deptFilter.isEmpty() || com.odysseus.backend.util.DepartmentMatcher.matches(b.getDepartment(), deptFilter))
                .filter(b -> {
                    if (finalTargetSemester != null) {
                        return Objects.equals(b.getSemester(), finalTargetSemester);
                    }
                    if (finalIsOdd != null && b.getSemester() != null) {
                        return finalIsOdd ? (b.getSemester() % 2 == 1) : (b.getSemester() % 2 == 0);
                    }
                    return true;
                })
                .sorted(Comparator.comparing(Batch::getBatchName))
                .collect(Collectors.toList());

        // Deduplicate batches by semester and section: exactly ONE batch per section per semester
        Map<String, Batch> uniqueBatches = new LinkedHashMap<>();
        for (Batch b : batches) {
            String bKey = (b.getSemester() != null ? b.getSemester() : 0) + "-" 
                    + (b.getSection() != null ? b.getSection().trim().toUpperCase() : b.getBatchName().trim());
            uniqueBatches.putIfAbsent(bKey, b);
        }
        batches = new ArrayList<>(uniqueBatches.values());

        List<Map<String, Object>> batchList = batches.stream().map(b -> {
            Map<String, Object> bm = new LinkedHashMap<>();
            bm.put("id", b.getId());
            bm.put("batchName", b.getBatchName());
            bm.put("section", b.getSection() != null ? b.getSection() : "");
            bm.put("semester", b.getSemester());
            bm.put("department", b.getDepartment());
            return bm;
        }).collect(Collectors.toList());

        // Workload tracking
        List<TimetableAssignment> assignments = timetableRepository.findByActiveTrue();
        Map<Long, Integer> loadTracker = new HashMap<>();
        for (Faculty f : allActiveFaculty) {
            int scheduled = assignments.stream()
                    .filter(a -> a.getFaculty() != null && Objects.equals(a.getFaculty().getId(), f.getId()))
                    .mapToInt(a -> a.getDurationPeriods() != null ? a.getDurationPeriods() : 1)
                    .sum();
            loadTracker.put(f.getId(), scheduled);
        }

        Set<Long> proposalSubjectIds = subjects.stream().map(Subject::getId).collect(Collectors.toSet());
        List<SubjectOffering> otherOfferings = offeringRepository.findByActiveTrue().stream()
                .filter(o -> o.getSubject() == null || !proposalSubjectIds.contains(o.getSubject().getId()))
                .collect(Collectors.toList());
        loadTracker.clear();
        loadTracker.putAll(allocationService.buildOfferingLoadMap(allActiveFaculty, otherOfferings));

        // Build proposals
        List<Map<String, Object>> proposals = new ArrayList<>();

        for (Subject subject : subjects) {
            String coursePrefix = (courseMappingService != null)
                    ? courseMappingService.extractCoursePrefix(subject.getSubjectCode())
                    : null;

            Optional<com.odysseus.backend.domain.CourseDepartmentMapping> mappingOpt = (courseMappingService != null && coursePrefix != null)
                    ? courseMappingService.resolveMapping(coursePrefix)
                    : Optional.empty();

            boolean isMapped = mappingOpt.isPresent()
                    && !"UNMAPPED".equalsIgnoreCase(mappingOpt.get().getDepartmentKey());

            String targetDeptKey = isMapped ? mappingOpt.get().getDepartmentKey() : null;

            if (!isMapped && departmentService != null) {
                String subDept = subject.getFacultyDepartment() != null && !subject.getFacultyDepartment().isBlank()
                        ? subject.getFacultyDepartment() : subject.getDepartment();
                if (subDept != null && !subDept.isBlank() && !subDept.equalsIgnoreCase("ALL")) {
                    com.odysseus.backend.service.DepartmentService.DepartmentInfo dInfo = departmentService.normalizeDepartment(subDept);
                    if (!"UNMAPPED".equalsIgnoreCase(dInfo.departmentKey())) {
                        targetDeptKey = dInfo.departmentKey();
                        isMapped = true;
                    }
                }
            }

            if (!isMapped && allocationService.isSpecialTestSubject(subject)) {
                targetDeptKey = (!deptFilter.isEmpty() && departmentService != null)
                        ? departmentService.normalizeDepartment(deptFilter).departmentKey()
                        : "CSE";
                isMapped = true;
            }

            String unmappedWarning = null;
            if (!isMapped) {
                unmappedWarning = "Unknown course department mapping: [" + (coursePrefix != null ? coursePrefix : subject.getSubjectCode())
                        + "]. Please map this course prefix to a department before assigning faculty.";
            }

            // Determine candidate faculty pool: strictly filter by matching departmentKey!
            List<Faculty> eligible = new ArrayList<>();
            final String finalTargetDeptKey = targetDeptKey;
            if (isMapped && finalTargetDeptKey != null) {
                eligible = allActiveFaculty.stream()
                        .filter(Faculty::isEligibleForTeaching)
                        .filter(f -> {
                            String fKey = f.getDepartmentKey();
                            String fDept = f.getDepartment();
                            return com.odysseus.backend.util.DepartmentMatcher.matches(fKey, finalTargetDeptKey)
                                    || com.odysseus.backend.util.DepartmentMatcher.matches(fDept, finalTargetDeptKey);
                        })
                        .collect(Collectors.toList());
            }

            // Fallback: Check explicit eligibility
            if (eligible.isEmpty()) {
                eligible = allActiveFaculty.stream()
                        .filter(Faculty::isEligibleForTeaching)
                        .filter(f -> f.getEligibleSubjects() != null && f.getEligibleSubjects().stream().anyMatch(s -> Objects.equals(s.getId(), subject.getId())))
                        .collect(Collectors.toList());
            }

            List<Batch> relevantSubjectBatches = batches.stream()
                    .filter(b -> subject.getSemester() == null || Objects.equals(b.getSemester(), subject.getSemester()))
                    .collect(Collectors.toList());

            int weeklyPeriods = allocationService.computeWeeklyHours(subject);
            int totalSectionDemand = relevantSubjectBatches.size() * weeklyPeriods;
            // A faculty member can realistically teach at most 16 hours of one subject
            int minNeeded = (int) Math.ceil((double) totalSectionDemand / 16.0);
            int requiredCount = Math.max(subject.getRequiredFacultyCount(), Math.max(1, minNeeded));
            if (!eligible.isEmpty()) {
                requiredCount = Math.min(requiredCount, eligible.size());
            }

            // Suggested per-section faculty distribution with cross-section uniqueness:
            // For the same subject, each section gets a distinct eligible faculty member whenever pool permits
            Map<Long, Long> suggestedSectionFaculty = new LinkedHashMap<>();
            List<Long> suggestedFacultyIds = new ArrayList<>();
            Set<Long> usedForThisSubject = new HashSet<>();

            final int finalWeeklyPeriods = weeklyPeriods;
            for (Batch b : relevantSubjectBatches) {
                Faculty chosen = null;
                int minLoad = Integer.MAX_VALUE;

                // 1. Try to find eligible faculty not yet used for this subject
                for (Faculty f : eligible) {
                    if (!usedForThisSubject.contains(f.getId())) {
                        int load = loadTracker.getOrDefault(f.getId(), 0);
                        int maxLoad = f.getMaxWeeklyLoad() != null ? f.getMaxWeeklyLoad() : 18;
                        if (load + finalWeeklyPeriods <= maxLoad && load < minLoad) {
                            minLoad = load;
                            chosen = f;
                        }
                    }
                }

                // 2. Fallback if pool is exhausted
                if (chosen == null) {
                    minLoad = Integer.MAX_VALUE;
                    for (Faculty f : eligible) {
                        int load = loadTracker.getOrDefault(f.getId(), 0);
                        int maxLoad = f.getMaxWeeklyLoad() != null ? f.getMaxWeeklyLoad() : 18;
                        if (load + finalWeeklyPeriods <= maxLoad && load < minLoad) {
                            minLoad = load;
                            chosen = f;
                        }
                    }
                }

                if (chosen != null) {
                    usedForThisSubject.add(chosen.getId());
                    loadTracker.merge(chosen.getId(), finalWeeklyPeriods, Integer::sum);
                    suggestedSectionFaculty.put(b.getId(), chosen.getId());
                    if (!suggestedFacultyIds.contains(chosen.getId())) {
                        suggestedFacultyIds.add(chosen.getId());
                    }
                }
            }

            // Build eligible faculty list for dropdown / checkboxes
            List<Map<String, Object>> eligibleList = eligible.stream().map(f -> {
                Map<String, Object> fm = new LinkedHashMap<>();
                fm.put("id", f.getId());
                fm.put("name", f.getName());
                fm.put("employeeId", f.getEmployeeId());
                fm.put("department", f.getDepartment());
                fm.put("designation", f.getDesignation() != null ? f.getDesignation() : "");
                fm.put("specialization", f.getSpecialization() != null ? f.getSpecialization() : "");
                fm.put("currentLoad", loadTracker.getOrDefault(f.getId(), 0));
                fm.put("maxLoad", f.getMaxWeeklyLoad() != null ? f.getMaxWeeklyLoad() : 18);
                return fm;
            }).collect(Collectors.toList());

            // Existing offerings / assigned faculty per section for this subject
            Map<Long, Long> existingSectionFaculty = new HashMap<>();
            for (Batch b : relevantSubjectBatches) {
                offeringRepository.findAll().stream()
                        .filter(o -> o.isActive() && o.getBatch() != null && Objects.equals(o.getBatch().getId(), b.getId())
                                && o.getSubject() != null && Objects.equals(o.getSubject().getId(), subject.getId())
                                && o.getAssignedFaculty() != null)
                        .findFirst()
                        .ifPresent(o -> existingSectionFaculty.put(b.getId(), o.getAssignedFaculty().getId()));
            }

            List<Map<String, Object>> relevantBatchList = relevantSubjectBatches.stream().map(b -> {
                Map<String, Object> bm = new LinkedHashMap<>();
                bm.put("id", b.getId());
                bm.put("batchName", b.getBatchName());
                bm.put("section", b.getSection() != null ? b.getSection() : "");
                bm.put("semester", b.getSemester());
                bm.put("department", b.getDepartment());
                return bm;
            }).collect(Collectors.toList());

            Map<String, Object> proposal = new LinkedHashMap<>();
            proposal.put("subjectId", subject.getId());
            proposal.put("subjectCode", subject.getSubjectCode());
            proposal.put("subjectName", subject.getSubjectName());
            proposal.put("subjectType", subject.getSubjectType());
            proposal.put("department", subject.getDepartment());
            proposal.put("coursePrefix", coursePrefix != null ? coursePrefix : "");
            proposal.put("departmentKey", targetDeptKey != null ? targetDeptKey : "UNMAPPED");
            proposal.put("isMapped", isMapped);
            proposal.put("mappingStatus", isMapped ? "MAPPED" : "UNMAPPED");
            proposal.put("unmappedWarning", unmappedWarning);
            proposal.put("semester", subject.getSemester());
            proposal.put("weeklyHours", subject.getCalculatedWeeklyHours());
            proposal.put("requiredFacultyCount", requiredCount);
            proposal.put("relevantBatches", relevantBatchList);
            proposal.put("eligibleFaculty", eligibleList);
            proposal.put("suggestedFacultyIds", suggestedFacultyIds);
            proposal.put("suggestedSectionFaculty", suggestedSectionFaculty);
            proposal.put("requiresAdminInput", eligible.isEmpty() || !isMapped);
            proposal.put("existingSectionFaculty", existingSectionFaculty);
            proposals.add(proposal);
        }

        // All active faculty across institution for manual selection dropdown
        List<Map<String, Object>> allFacultyList = allActiveFaculty.stream().map(f -> {
            Map<String, Object> fm = new LinkedHashMap<>();
            fm.put("id", f.getId());
            fm.put("name", f.getName());
            fm.put("employeeId", f.getEmployeeId());
            fm.put("department", f.getDepartment());
            fm.put("designation", f.getDesignation() != null ? f.getDesignation() : "");
            return fm;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "department", department,
                "totalSubjects", subjects.size(),
                "proposals", proposals,
                "allFaculty", allFacultyList,
                "batches", batchList
        ));
    }

    // ------------------------------------------------------------------------------------
    // QUICK-CREATE FACULTY: For on-the-fly manual entry when no matching faculty exists
    // ------------------------------------------------------------------------------------
    @PostMapping("/quick-create")
    public ResponseEntity<?> quickCreateFaculty(@RequestBody Map<String, Object> payload) {
        String name = (String) payload.get("name");
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Faculty name is required."));
        }
        name = name.trim();

        String department = (String) payload.get("department");
        if (department == null || department.trim().isEmpty()) {
            department = "Computer Science Engineering";
        }

        String employeeId = (String) payload.get("employeeId");
        if (employeeId == null || employeeId.trim().isEmpty()) {
            employeeId = "FAC-" + System.currentTimeMillis() % 100000;
        }

        String email = (String) payload.get("email");
        if (email == null || email.trim().isEmpty() || !email.contains("@")) {
            email = name.toLowerCase().replaceAll("[^a-z0-9]", ".") + "@odysseus.edu";
        }

        Faculty f = Faculty.builder()
                .name(name)
                .employeeId(employeeId)
                .email(email)
                .department(department)
                .designation((String) payload.getOrDefault("designation", "Faculty"))
                .specialization((String) payload.getOrDefault("specialization", ""))
                .maxWeeklyLoad(payload.get("maxWeeklyLoad") != null ? ((Number) payload.get("maxWeeklyLoad")).intValue() : 18)
                .active(true)
                .eligibleSubjects(new HashSet<>())
                .build();

        Faculty saved = facultyRepository.save(f);
        return ResponseEntity.ok(saved);
    }

    // ------------------------------------------------------------------------------------
    // CONFIRM ASSIGNMENT: Admin confirms subject→faculty mapping.
    // Creates/updates SubjectOffering records (per batch/section) for all confirmed assignments.
    // Supports section-specific faculty assignment and distributing multiple faculty across sections.
    // ------------------------------------------------------------------------------------
    @PostMapping("/confirm-assignment")
    @Transactional
    public ResponseEntity<?> confirmAssignment(@RequestBody Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) (payload.containsKey("assignments")
                ? payload.get("assignments") : payload.get("confirmed"));

        String academicYear = payload.containsKey("academicYear") ? (String) payload.get("academicYear") : "2026-2027";
        String department = payload.containsKey("department") ? (String) payload.get("department") : null;

        if (assignments == null || assignments.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No assignments to confirm."));
        }

        // Get all active batches (sections) for the department
        List<Batch> batches = batchRepository.findAll().stream()
                .filter(Batch::isActive)
                .filter(b -> department == null || department.isEmpty() || com.odysseus.backend.util.DepartmentMatcher.matches(b.getDepartment(), department))
                .sorted(Comparator.comparing(Batch::getBatchName))
                .collect(Collectors.toList());

        int offeringsCreated = 0;
        int offeringsUpdated = 0;
        int eligibilityUpdated = 0;

        for (Map<String, Object> entry : assignments) {
            Long subjectId = parseLong(entry.get("subjectId"));
            if (subjectId == null) continue;

            Subject subject = subjectRepository.findById(subjectId).orElse(null);
            if (subject == null) continue;

            // Update required faculty count if configured in entry
            if (entry.containsKey("requiredFacultyCount")) {
                Integer reqCount = parseInteger(entry.get("requiredFacultyCount"));
                if (reqCount != null && reqCount > 0) {
                    subject.setRequiredFacultyCount(reqCount);
                    subjectRepository.save(subject);
                }
            }

            // Collect selected faculty IDs
            List<Long> facultyIds = new ArrayList<>();

            // 1. Check if multiple faculty IDs passed
            if (entry.containsKey("facultyIds") && entry.get("facultyIds") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> rawIds = (List<Object>) entry.get("facultyIds");
                for (Object rawId : rawIds) {
                    Long fid = parseLong(rawId);
                    if (fid != null && !facultyIds.contains(fid)) facultyIds.add(fid);
                }
            }
            // 2. Check legacy single facultyId
            if (entry.containsKey("facultyId")) {
                Long fid = parseLong(entry.get("facultyId"));
                if (fid != null && !facultyIds.contains(fid)) facultyIds.add(fid);
            }

            // 3. Check manual faculty creation if specified
            if (entry.containsKey("manualFaculty") && entry.get("manualFaculty") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> manual = (Map<String, Object>) entry.get("manualFaculty");
                String mName = (String) manual.get("name");
                if (mName != null && !mName.trim().isEmpty()) {
                    String mEmpId = (String) manual.get("employeeId");
                    String finalEmpId = (mEmpId == null || mEmpId.trim().isEmpty())
                            ? "FAC-" + System.currentTimeMillis() % 100000 : mEmpId.trim();
                    String rawEmail = (String) manual.get("email");
                    String finalEmail = (rawEmail == null || rawEmail.trim().isEmpty() || !rawEmail.contains("@"))
                            ? mName.toLowerCase().replaceAll("[^a-z0-9]", ".") + "@odysseus.edu" : rawEmail.trim();
                    String rawDept = (String) manual.get("department");
                    String finalDept = (rawDept == null || rawDept.trim().isEmpty()) ? subject.getDepartment() : rawDept.trim();
                    String finalName = mName.trim();

                    Faculty newFac = facultyRepository.findByEmployeeId(finalEmpId).orElseGet(() -> Faculty.builder()
                            .name(finalName)
                            .employeeId(finalEmpId)
                            .email(finalEmail)
                            .department(finalDept)
                            .maxWeeklyLoad(18)
                            .active(true)
                            .eligibleSubjects(new HashSet<>())
                            .build());

                    newFac = facultyRepository.save(newFac);
                    if (!facultyIds.contains(newFac.getId())) {
                        facultyIds.add(newFac.getId());
                    }
                }
            }

            if (facultyIds.isEmpty()) continue;

            // Department guard: keep only faculty from the subject's mapped pool
            List<Faculty> deptPool = allocationService.buildCandidatePool(
                    subject, facultyRepository.findByActiveTrue(), department, false);
            Set<Long> deptPoolIds = deptPool.stream().map(Faculty::getId).collect(Collectors.toSet());
            if (!deptPoolIds.isEmpty()) {
                facultyIds.removeIf(id -> {
                    boolean outside = !deptPoolIds.contains(id);
                    if (outside) {
                        log.warn("[ConfirmAssign] Rejected faculty {} for subject [{}]: outside mapped department",
                                id, subject.getSubjectCode());
                    }
                    return outside;
                });
            }
            if (facultyIds.isEmpty()) continue;

            // Load selected faculty entities & persist eligibility
            List<Faculty> confirmedFacultyList = new ArrayList<>();
            for (Long fId : facultyIds) {
                Faculty f = facultyRepository.findById(fId).orElse(null);
                if (f != null) {
                    boolean alreadyEligible = f.getEligibleSubjects().stream()
                            .anyMatch(s -> Objects.equals(s.getId(), subjectId));
                    if (!alreadyEligible) {
                        f.getEligibleSubjects().add(subject);
                        facultyRepository.save(f);
                        eligibilityUpdated++;
                    }
                    confirmedFacultyList.add(f);
                }
            }

            if (confirmedFacultyList.isEmpty()) continue;

            // Check if section-specific assignments were explicitly supplied
            Map<Long, Long> explicitSectionMap = new HashMap<>();
            if (entry.containsKey("sectionAssignments") && entry.get("sectionAssignments") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> secAssigns = (List<Map<String, Object>>) entry.get("sectionAssignments");
                for (Map<String, Object> sa : secAssigns) {
                    Long bId = parseLong(sa.get("batchId"));
                    Long fId = parseLong(sa.get("facultyId"));
                    if (bId != null && fId != null) explicitSectionMap.put(bId, fId);
                }
            }

            String targetDept = subject.getDepartment() != null ? subject.getDepartment() : department;
            Integer targetSem = subject.getSemester() != null ? subject.getSemester() : 3;

            if (targetDept != null && targetSem != null) {
                ensureDepartmentSections(targetDept, targetSem);
            }

            // Filter relevant batches by semester and department
            List<Batch> relevantBatches = batchRepository.findAll().stream()
                    .filter(Batch::isActive)
                    .filter(b -> targetDept == null || com.odysseus.backend.util.DepartmentMatcher.matches(b.getDepartment(), targetDept))
                    .filter(b -> targetSem == null || Objects.equals(b.getSemester(), targetSem))
                    .sorted(Comparator.comparing(b -> b.getSection() != null ? b.getSection() : b.getBatchName()))
                    .collect(Collectors.toList());

            if (relevantBatches.isEmpty()) relevantBatches = batches;

            // Apply to batches/sections: distribute eligible faculty across sections
            // Use workload-aware selection instead of naive round-robin
            List<SubjectOffering> otherOfferings = offeringRepository.findByActiveTrue().stream()
                    .filter(o -> o.getSubject() != null && !Objects.equals(o.getSubject().getId(), subjectId))
                    .collect(Collectors.toList());
            Map<Long, Integer> sectionLoadMap = new HashMap<>(
                    allocationService.buildOfferingLoadMap(confirmedFacultyList, otherOfferings));
            int subjectHours = allocationService.computeWeeklyHours(subject);

            for (int i = 0; i < relevantBatches.size(); i++) {
                Batch batch = relevantBatches.get(i);

                // Determine assigned faculty for this section
                Faculty assignedFaculty = null;
                if (explicitSectionMap.containsKey(batch.getId())) {
                    // Admin explicitly chose this faculty for this section
                    Long assignedFid = explicitSectionMap.get(batch.getId());
                    assignedFaculty = confirmedFacultyList.stream()
                            .filter(f -> Objects.equals(f.getId(), assignedFid))
                            .findFirst()
                            .orElse(null);
                    if (assignedFaculty == null) assignedFaculty = confirmedFacultyList.get(0);
                } else {
                    // Workload-aware distribution: pick confirmed faculty with lowest current load
                    // that still has capacity for this subject's hours
                    assignedFaculty = confirmedFacultyList.stream()
                            .filter(f -> {
                                int load = sectionLoadMap.getOrDefault(f.getId(), 0);
                                int max = f.getMaxWeeklyLoad() != null ? f.getMaxWeeklyLoad() : 18;
                                return (load + subjectHours) <= max;
                            })
                            .min(Comparator.comparingInt(f -> sectionLoadMap.getOrDefault(f.getId(), 0)))
                            .orElseGet(() -> {
                                // All at capacity — fall back to least loaded
                                return confirmedFacultyList.stream()
                                        .min(Comparator.comparingInt(f -> sectionLoadMap.getOrDefault(f.getId(), 0)))
                                        .orElse(confirmedFacultyList.get(0));
                            });
                }
                // Update load tracker so the next section sees the updated workload
                sectionLoadMap.merge(assignedFaculty.getId(), subjectHours, Integer::sum);
                log.info("[ConfirmAssign] Subject [{}] Section [{}] → {} (load now: {})",
                        subject.getSubjectCode(), batch.getBatchName(), assignedFaculty.getName(),
                        sectionLoadMap.get(assignedFaculty.getId()));

                // Check existing offering
                List<SubjectOffering> existing = offeringRepository.findAll().stream()
                        .filter(o -> o.isActive()
                                && o.getBatch() != null && Objects.equals(o.getBatch().getId(), batch.getId())
                                && o.getSubject() != null && Objects.equals(o.getSubject().getId(), subjectId))
                        .collect(Collectors.toList());

                if (!existing.isEmpty()) {
                    SubjectOffering off = existing.get(0);
                    off.setAssignedFaculty(assignedFaculty);
                    off.setAcademicYear(academicYear);
                    off.setSemester(subject.getSemester() != null ? subject.getSemester() : batch.getSemester());
                    offeringRepository.save(off);
                    offeringsUpdated++;
                } else {
                    SubjectOffering newOff = SubjectOffering.builder()
                            .batch(batch)
                            .subject(subject)
                            .assignedFaculty(assignedFaculty)
                            .academicYear(academicYear)
                            .semester(subject.getSemester() != null ? subject.getSemester() : batch.getSemester())
                            .active(true)
                            .build();
                    offeringRepository.save(newOff);
                    offeringsCreated++;
                }
            }
        }

        return ResponseEntity.ok(Map.of(
                "message", "Faculty assignment confirmed and successfully synced across sections.",
                "offeringsCreated", offeringsCreated,
                "offeringsUpdated", offeringsUpdated,
                "eligibilityUpdated", eligibilityUpdated,
                "totalBatches", batches.size(),
                "totalConfirmed", assignments.size()
        ));
    }

    @PostMapping("/batch-assign")
    @Transactional
    public ResponseEntity<?> batchAssignSectionFaculty(@RequestBody List<Map<String, Object>> assignments) {
        if (assignments == null || assignments.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No assignments provided."));
        }

        int updatedCount = 0;
        int createdCount = 0;
        List<String> warnings = new ArrayList<>();

        for (Map<String, Object> item : assignments) {
            Long batchId = parseLong(item.get("batchId"));
            Long subjectId = parseLong(item.get("subjectId"));
            Long facultyId = parseLong(item.get("facultyId"));
            String academicYear = item.containsKey("academicYear") ? (String) item.get("academicYear") : "2026 - 2027";
            Integer semester = parseInteger(item.get("semester"));

            if (batchId == null || subjectId == null) {
                continue;
            }

            Batch batch = batchRepository.findById(batchId).orElse(null);
            Subject subject = subjectRepository.findById(subjectId).orElse(null);
            if (batch == null || subject == null) {
                continue;
            }
            if (subject.getSemester() == null || batch.getSemester() == null || !Objects.equals(subject.getSemester(), batch.getSemester())) {
                warnings.add("Skipped " + subject.getSubjectCode() + " for " + batch.getBatchName() + ": semester does not match the section.");
                continue;
            }

            Faculty faculty = null;
            if (facultyId != null) {
                faculty = facultyRepository.findById(facultyId).orElse(null);
                if (faculty != null) {
                    if (faculty.isPrincipal()) {
                        warnings.add("Excluded Principal " + faculty.getName() + " from assignment on " + subject.getSubjectCode());
                        continue;
                    }
                    if (faculty.isHod()) {
                        warnings.add("Excluded HOD " + faculty.getName() + " from assignment on " + subject.getSubjectCode());
                        continue;
                    }
                    if (!faculty.isEligibleForTeaching()) {
                        warnings.add("Faculty " + faculty.getName() + " is not eligible for teaching.");
                        continue;
                    }
                }
            }

            List<SubjectOffering> existing = offeringRepository.findByBatchIdAndSubjectId(batchId, subjectId);
            if (!existing.isEmpty()) {
                for (SubjectOffering off : existing) {
                    off.setAssignedFaculty(faculty);
                    off.setActive(true);
                    if (academicYear != null) off.setAcademicYear(academicYear);
                    off.setSemester(subject.getSemester());
                    offeringRepository.save(off);
                    updatedCount++;
                }
            } else {
                SubjectOffering newOff = SubjectOffering.builder()
                        .batch(batch)
                        .subject(subject)
                        .assignedFaculty(faculty)
                        .academicYear(academicYear)
                        .semester(subject.getSemester())
                        .active(true)
                        .build();
                offeringRepository.save(newOff);
                createdCount++;
            }
        }

        return ResponseEntity.ok(Map.of(
                "message", "Successfully saved assignments to Subject Offerings database.",
                "updated", updatedCount,
                "created", createdCount,
                "warnings", warnings
        ));
    }

    @PostMapping("/reset-assignments")
    @Transactional
    public ResponseEntity<?> resetAllSectionAssignments() {
        offeringRepository.resetAllAssignments();
        return ResponseEntity.ok(Map.of("message", "All faculty assignments in Subject Offerings have been reset to Unassigned."));
    }

    private Long parseLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try { return Long.valueOf(value.toString().trim()); } catch (NumberFormatException e) { return null; }
    }

    private Integer parseInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        try { return Integer.valueOf(value.toString().trim()); } catch (NumberFormatException e) { return null; }
    }

    @PostMapping(value = "/parse-document", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> parseFacultyDocument(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "department", defaultValue = "ALL") String department) {
        try {
            List<Map<String, Object>> facultyList = pdfExtractionService.extractFacultyFromFile(file, department);

            // Compute department-wise breakdown for merged upload response
            Map<String, Long> departmentCounts = new LinkedHashMap<>();
            for (Map<String, Object> fac : facultyList) {
                String d = (String) fac.get("department");
                if (d != null && !d.trim().isEmpty()) {
                    departmentCounts.put(d, departmentCounts.getOrDefault(d, 0L) + 1);
                }
            }

            return ResponseEntity.ok(Map.of(
                    "totalExtracted", facultyList.size(),
                    "preview", facultyList,
                    "departmentCounts", departmentCounts,
                    "fileName", file.getOriginalFilename() != null ? file.getOriginalFilename() : ""
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Unable to extract faculty document: " + e.getMessage()));
        }
    }

        @RequestMapping(value = "/load-stored-merged-pdf", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<?> loadStoredMergedPdf() {
        java.io.File file = new java.io.File("C:/Users/anany/Downloads/Faculty_merged_corrected.pdf");
        if (!file.exists()) {
            file = new java.io.File("C:/Users/anany/Downloads/_Faculty_merged.pdf");
        }
        if (!file.exists()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Faculty_merged_corrected.pdf not found in system Downloads directory."));
        }
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            List<Map<String, Object>> facultyList = pdfExtractionService.parseFacultyFromPdfDocument(bytes, "ALL");
            if (facultyList == null) {
                facultyList = Collections.emptyList();
            }

            Map<String, Long> departmentCounts = new LinkedHashMap<>();
            for (Map<String, Object> fac : facultyList) {
                String d = (String) fac.get("department");
                if (d != null && !d.trim().isEmpty()) {
                    departmentCounts.put(d, departmentCounts.getOrDefault(d, 0L) + 1);
                }
            }

            return ResponseEntity.ok(Map.of(
                    "totalExtracted", facultyList.size(),
                    "totalDepartments", departmentCounts.size(),
                    "preview", facultyList,
                    "departmentCounts", departmentCounts,
                    "fileName", file.getName()
            ));
        } catch (Exception e) {
            log.error("Failed to load stored merged PDF: {}", e.getMessage(), e);
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to load stored merged PDF: " + e.getMessage()));
        }
    }

    @PostMapping("/batch-save")
    public ResponseEntity<?> batchSaveFaculty(@RequestBody List<Map<String, Object>> facultyList) {
        List<Faculty> savedList = new ArrayList<>();
        List<String> updatedIds = new ArrayList<>();

        for (Map<String, Object> map : facultyList) {
            String name = (String) map.get("name");
            if (name == null || name.trim().isEmpty()) {
                name = "Faculty";
            }
            name = name.trim();

            String dept = (String) map.get("department");
            if (dept == null || dept.trim().isEmpty()) {
                dept = "Computer Science Engineering";
            }
            dept = dept.trim();

            // 1. Try to find existing faculty by normalized name + department / departmentKey (idempotent re-upload)
            String normName = name.toLowerCase().replaceAll("\\s+", " ").trim();
            final String finalName = name;
            final String finalDept = dept;
            String deptKey = (String) map.get("departmentKey");
            if (deptKey == null || deptKey.trim().isEmpty()) {
                deptKey = departmentService != null ? departmentService.normalizeDepartment(dept).departmentKey() : getDeptCode(dept);
            } else {
                deptKey = deptKey.trim().toUpperCase();
            }
            final String finalDeptKey = deptKey;

            Optional<Faculty> existingByNameDept = facultyRepository.findAll().stream()
                    .filter(f -> f.getName() != null
                            && f.getName().toLowerCase().replaceAll("\\s+", " ").trim().equals(normName)
                            && (com.odysseus.backend.util.DepartmentMatcher.matches(f.getDepartment(), finalDept)
                                || (f.getDepartmentKey() != null && f.getDepartmentKey().equalsIgnoreCase(finalDeptKey))))
                    .findFirst();

            Faculty f;
            if (existingByNameDept.isPresent()) {
                // Re-upload of same department — update existing record
                f = existingByNameDept.get();
                updatedIds.add(f.getEmployeeId());
            } else {
                // 2. Check if supplied employeeId exists
                String empId = (String) map.get("employeeId");
                if (empId != null && !empId.trim().isEmpty()) {
                    Optional<Faculty> existingByEmpId = facultyRepository.findByEmployeeId(empId.trim());
                    if (existingByEmpId.isPresent()) {
                        Faculty existingFac = existingByEmpId.get();
                        // Same department — treat as update
                        if (com.odysseus.backend.util.DepartmentMatcher.matches(existingFac.getDepartment(), finalDept)
                                || (existingFac.getDepartmentKey() != null && existingFac.getDepartmentKey().equalsIgnoreCase(finalDeptKey))) {
                            f = existingFac;
                            updatedIds.add(empId);
                        } else {
                            // DIFFERENT department has this ID — generate a new unique one
                            empId = generateUniqueEmployeeId(finalDept);
                            f = new Faculty();
                            f.setEmployeeId(empId);
                            f.setEligibleSubjects(new HashSet<>());
                        }
                    } else {
                        // New faculty with unused ID
                        f = new Faculty();
                        f.setEmployeeId(empId.trim());
                        f.setEligibleSubjects(new HashSet<>());
                    }
                } else {
                    // No employeeId provided — generate a unique one
                    String newEmpId = generateUniqueEmployeeId(finalDept);
                    f = new Faculty();
                    f.setEmployeeId(newEmpId);
                    f.setEligibleSubjects(new HashSet<>());
                }
            }

            f.setName(finalName);
            String email = (String) map.get("email");
            if (email == null || email.trim().isEmpty() || !email.contains("@")) {
                email = finalName.toLowerCase().replaceAll("[^a-z0-9]", ".") + "@odysseus.edu";
            }
            f.setEmail(email);
            f.setDepartment(finalDept);
            f.setDepartmentKey(finalDeptKey);

            if (map.containsKey("sourceDocument") && map.get("sourceDocument") != null) {
                f.setSourceDocument((String) map.get("sourceDocument"));
            } else if (f.getSourceDocument() == null) {
                f.setSourceDocument("Merged Faculty Master PDF");
            }

            if (map.containsKey("verificationStatus") && map.get("verificationStatus") != null) {
                f.setVerificationStatus((String) map.get("verificationStatus"));
            } else if (f.getVerificationStatus() == null) {
                f.setVerificationStatus("APPROVED");
            }

                        if (map.containsKey("qualification")) {
                f.setQualification((String) map.get("qualification"));
            }
            if (map.containsKey("facultyNumber") && map.get("facultyNumber") != null) {
                f.setFacultyNumber(((Number) map.get("facultyNumber")).intValue());
            }
            if (map.containsKey("category")) {
                f.setCategory((String) map.get("category"));
            }
            if (map.containsKey("hod")) {
                f.setHod(Boolean.TRUE.equals(map.get("hod")));
            }
            if (map.containsKey("eligibleForTeaching")) {
                f.setEligibleForTeaching(Boolean.TRUE.equals(map.get("eligibleForTeaching")));
            } else {
                f.setEligibleForTeaching(f.isEligibleForTeaching());
            }

            if (map.containsKey("designation")) {
                f.setDesignation((String) map.get("designation"));
            }
            if (map.containsKey("specialization")) {
                f.setSpecialization((String) map.get("specialization"));
            }
            if (map.containsKey("subjects")) {
                f.setSubjectsHandled((String) map.get("subjects"));
            }

            f.setMaxWeeklyLoad(map.get("maxWeeklyLoad") != null ? ((Number) map.get("maxWeeklyLoad")).intValue() : 18);
            f.setActive(true);

            // If subjectIds provided in map, link them
            if (map.containsKey("subjectIds") && map.get("subjectIds") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Number> subIds = (List<Number>) map.get("subjectIds");
                for (Number sid : subIds) {
                    subjectRepository.findById(sid.longValue()).ifPresent(f.getEligibleSubjects()::add);
                }
            } else if (f.getSpecialization() != null || f.getSubjectsHandled() != null) {
                // If PDF contained specialization or subjects handled, dynamically match department subjects
                List<Subject> deptSubjects = subjectRepository.findAll().stream()
                        .filter(Subject::isActive)
                        .filter(s -> com.odysseus.backend.util.DepartmentMatcher.matches(s.getDepartment(), f.getDepartment()))
                        .collect(Collectors.toList());
                for (Subject s : deptSubjects) {
                    if (matchesSpecializationOrSubjects(f, s)) {
                        f.getEligibleSubjects().add(s);
                    }
                }
            }

            savedList.add(facultyRepository.save(f));
        }

        return ResponseEntity.ok(Map.of(
                "savedCount", savedList.size(),
                "savedFaculty", savedList,
                "updatedIds", updatedIds
        ));
    }

    /**
     * Generates a unique employee ID scoped to a department.
     * Format: {DEPT_CODE}-FAC-{timestamp_suffix}
     */
    private String generateUniqueEmployeeId(String department) {
        String deptCode = getDeptCode(department);
        String candidate;
        int attempts = 0;
        do {
            long suffix = System.currentTimeMillis() % 100000 + (long)(Math.random() * 1000);
            candidate = String.format("%s-FAC-%05d", deptCode, suffix);
            attempts++;
        } while (facultyRepository.existsByEmployeeId(candidate) && attempts < 50);
        return candidate;
    }

    private void ensureDepartmentSections(String department, Integer semester) {
        if (department == null || semester == null || department.equalsIgnoreCase("ALL") || department.contains("[object")) return;
        char[] sections = {'A', 'B', 'C', 'D', 'E', 'F', 'G'};
        String deptCode = getDeptCode(department);
        for (char sec : sections) {
            String secStr = String.valueOf(sec);
            boolean exists = batchRepository.findAll().stream()
                    .anyMatch(b -> b.isActive()
                            && com.odysseus.backend.util.DepartmentMatcher.matches(b.getDepartment(), department)
                            && Objects.equals(b.getSemester(), semester)
                            && secStr.equalsIgnoreCase(b.getSection()));
            if (!exists) {
                batchRepository.save(Batch.builder()
                        .department(department)
                        .semester(semester)
                        .section(secStr)
                        .batchName(deptCode + "-" + secStr)
                        .strength(60)
                        .academicYear("2026-2027")
                        .active(true)
                        .build());
            }
        }
    }

    private String getDeptCode(String department) {
        if (department == null || department.trim().isEmpty()) return "DEPT";
        if (departmentService != null) {
            return departmentService.normalizeDepartment(department).departmentKey();
        }
        String letters = department.replaceAll("[^A-Z]", "");
        return letters.isEmpty() ? department.substring(0, Math.min(4, department.length())).toUpperCase() : letters;
    }
}
