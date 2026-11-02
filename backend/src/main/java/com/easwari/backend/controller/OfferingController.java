package com.easwari.backend.controller;

import com.easwari.backend.domain.Batch;
import com.easwari.backend.domain.Faculty;
import com.easwari.backend.domain.Subject;
import com.easwari.backend.domain.SubjectOffering;
import com.easwari.backend.repository.BatchRepository;
import com.easwari.backend.repository.FacultyRepository;
import com.easwari.backend.repository.SubjectOfferingRepository;
import com.easwari.backend.repository.SubjectRepository;
import com.easwari.backend.service.FacultyAllocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/offerings")
@RequiredArgsConstructor
@Slf4j
public class OfferingController {

    private final SubjectOfferingRepository offeringRepository;
    private final BatchRepository batchRepository;
    private final SubjectRepository subjectRepository;
    private final FacultyRepository facultyRepository;
    private final FacultyAllocationService allocationService;
    private final com.easwari.backend.service.DepartmentService departmentService;

    @GetMapping
    public ResponseEntity<List<SubjectOffering>> getAllOfferings(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Integer semester) {
        // auto-sync removed from GET: offerings are created only by explicit POST actions
        List<SubjectOffering> list = offeringRepository.findAll();
        if (semester != null) {
            list = list.stream().filter(o -> {
                if (o.getSemester() != null && Objects.equals(o.getSemester(), semester)) return true;
                if (o.getBatch() != null && Objects.equals(o.getBatch().getSemester(), semester)) return true;
                if (o.getSubject() != null && Objects.equals(o.getSubject().getSemester(), semester)) return true;
                return false;
            }).collect(Collectors.toList());
        }
        if (department != null && !department.trim().isEmpty() && !department.equalsIgnoreCase("ALL")) {
            list = list.stream().filter(o -> {
                if (o.getBatch() != null && com.easwari.backend.util.DepartmentMatcher.matches(o.getBatch().getDepartment(), department)) return true;
                if (o.getSubject() != null && com.easwari.backend.util.DepartmentMatcher.matches(o.getSubject().getDepartment(), department)) return true;
                return false;
            }).collect(Collectors.toList());
        }
        return ResponseEntity.ok(list);
    }

    @PostMapping
    public ResponseEntity<?> createOffering(@RequestBody Map<String, Object> payload) {
        Long batchId = parseLong(payload.get("batchId"));
        Long subjectId = parseLong(payload.get("subjectId"));
        Long assignedFacultyId = parseLongOrNull(payload.get("assignedFacultyId"));

        if (batchId == null || subjectId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Batch and Subject are required."));
        }

        if (offeringRepository.existsByBatchIdAndSubjectIdAndActiveTrue(batchId, subjectId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "This batch is already assigned to this subject."));
        }

        Batch batch = batchRepository.findById(batchId).orElseThrow(() -> new IllegalArgumentException("Batch not found"));
        Subject subject = subjectRepository.findById(subjectId).orElseThrow(() -> new IllegalArgumentException("Subject not found"));
        Faculty assignedFaculty = assignedFacultyId != null ? facultyRepository.findById(assignedFacultyId).orElse(null) : null;

        if (subject.getSemester() != null && batch.getSemester() != null && !Objects.equals(subject.getSemester(), batch.getSemester())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Subject semester does not match the section's semester."));
        }
        Integer semester = parseInteger(payload.get("semester"));

        SubjectOffering offering = SubjectOffering.builder()
                .batch(batch)
                .subject(subject)
                .assignedFaculty(assignedFaculty)
                .academicYear((String) payload.get("academicYear"))
                .semester(subject.getSemester() != null ? subject.getSemester() : batch.getSemester())
                .active(true)
                .build();

        SubjectOffering saved = offeringRepository.save(offering);
        return ResponseEntity.ok(saved);
    }

    /**
     * POST /api/admin/offerings/auto-assign
     *
     * Automatically allocates faculty to sections using the FacultyAllocationService.
     * This is IDEMPOTENT: running it twice with the same data produces the same result.
     *
     * Algorithm:
     * 1. Load subjects, batches, faculty for the scope
     * 2. Build existing offering map (to respect already-confirmed per-section overrides)
     * 3. Run FacultyAllocationService.allocate() — workload-balanced, constraint-aware
     * 4. Upsert SubjectOffering records (create if missing, update if unassigned)
     *    - NEVER overwrites a manually-set faculty assignment unless forceReassign=true
     * 5. Return diagnostics (created/updated counts, warnings, infeasible subjects)
     */
    @PostMapping("/auto-assign")
    @Transactional
    public ResponseEntity<?> autoAssignFacultyAndSyncOfferings(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false, defaultValue = "2026-2027") String academicYear,
            @RequestParam(required = false, defaultValue = "false") boolean forceReassign) {

        if (department == null || department.trim().isEmpty() || department.equalsIgnoreCase("ALL") || semester == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Select a department and semester before running Auto-Assign."));
        }
        // 1. Load scope data
        List<Subject> subjects = subjectRepository.findAll().stream()
                .filter(Subject::isActive)
                .filter(s -> {
                    if (semester != null && !Objects.equals(s.getSemester(), semester)) return false;
                    if (department != null && !department.trim().isEmpty() && !department.equalsIgnoreCase("ALL")) {
                        return com.easwari.backend.util.DepartmentMatcher.matches(s.getDepartment(), department);
                    }
                    return true;
                }).collect(Collectors.toList());

        if (subjects.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "No active subjects found for the selected scope. Configure Subjects & Credits first."));
        }

        List<Batch> batches = batchRepository.findAll().stream()
                .filter(Batch::isActive)
                .filter(b -> {
                    if (semester != null && !Objects.equals(b.getSemester(), semester)) return false;
                    if (department != null && !department.trim().isEmpty() && !department.equalsIgnoreCase("ALL")) {
                        return com.easwari.backend.util.DepartmentMatcher.matches(b.getDepartment(), department);
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

        if (batches.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "No active sections found for the selected scope. Configure Batches & Sections first."));
        }

        // 2. Build existing assignment map: batchSubjectKey -> facultyId
        List<SubjectOffering> existingOfferings = offeringRepository.findByActiveTrue();
        Map<Long, Long> existingBatchSubjectFacultyMap = new HashMap<>();
        for (SubjectOffering o : existingOfferings) {
            if (o.getAssignedFaculty() != null) {
                existingBatchSubjectFacultyMap.put(
                        allocationService.encodeBatchSubjectKey(o.getBatch().getId(), o.getSubject().getId()),
                        o.getAssignedFaculty().getId());
            }
        }

        // 3. Run allocation algorithm
        FacultyAllocationService.AllocationResult result = allocationService.allocate(
                subjects, batches, department,
                !forceReassign,  // respectExistingAssignments unless forceReassign
                existingBatchSubjectFacultyMap
        );

        // 4. Persist decisions as SubjectOffering records
        int createdCount = 0;
        int updatedCount = 0;

        for (FacultyAllocationService.AllocationDecision decision : result.decisions()) {
            List<SubjectOffering> existing = existingOfferings.stream()
                    .filter(o -> o.isActive()
                            && o.getBatch() != null && Objects.equals(o.getBatch().getId(), decision.batchId())
                            && o.getSubject() != null && Objects.equals(o.getSubject().getId(), decision.subjectId()))
                    .collect(Collectors.toList());

            if (!existing.isEmpty()) {
                SubjectOffering off = existing.get(0);
                // Only update if unassigned or forceReassign
                if (off.getAssignedFaculty() == null || forceReassign) {
                    off.setAssignedFaculty(decision.assignedFaculty());
                    off.setAcademicYear(academicYear);
                    offeringRepository.save(off);
                    updatedCount++;
                }
            } else {
                // Create new offering
                Batch batch = batchRepository.findById(decision.batchId()).orElse(null);
                Subject subject = subjectRepository.findById(decision.subjectId()).orElse(null);
                if (batch != null && subject != null && subject.getSemester() != null && Objects.equals(subject.getSemester(), batch.getSemester())) {
                    SubjectOffering newOff = SubjectOffering.builder()
                            .batch(batch)
                            .subject(subject)
                            .assignedFaculty(decision.assignedFaculty())
                            .academicYear(academicYear)
                            .semester(subject.getSemester() != null ? subject.getSemester() : batch.getSemester())
                            .active(true)
                            .build();
                    offeringRepository.save(newOff);
                    createdCount++;
                }
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Automatic faculty allocation completed.");
        response.put("offeringsCreated", createdCount);
        response.put("offeringsUpdated", updatedCount);
        response.put("totalDecisions", result.decisions().size());
        response.put("totalBatches", batches.size());
        response.put("totalSubjects", subjects.size());
        response.put("warnings", result.warnings());
        response.put("infeasibleSubjects", result.infeasibleSubjects());
        response.put("hasWarnings", !result.warnings().isEmpty());

        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateOffering(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return offeringRepository.findById(id).map(o -> {
            if (payload.containsKey("assignedFacultyId")) {
                Long fid = parseLongOrNull(payload.get("assignedFacultyId"));
                if (fid == null) {
                    o.setAssignedFaculty(null);
                } else {
                    Faculty f = facultyRepository.findById(fid).orElse(null);
                    if (f == null) {
                        return ResponseEntity.badRequest().body(Map.of("error", "Faculty not found with id " + fid));
                    }
                    o.setAssignedFaculty(f);
                    // Also add to eligible subjects if not already
                    if (o.getSubject() != null && !f.getEligibleSubjects().contains(o.getSubject())) {
                        f.getEligibleSubjects().add(o.getSubject());
                        facultyRepository.save(f);
                    }
                }
            }
            if (payload.containsKey("academicYear")) {
                o.setAcademicYear((String) payload.get("academicYear"));
            }
            SubjectOffering saved = offeringRepository.save(o);
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/admin/offerings/eligible-faculty/{subjectId}
     * Returns faculty eligible to teach a given subject, with current workload info.
     */
    @GetMapping("/eligible-faculty/{subjectId}")
    public ResponseEntity<?> getEligibleFacultyForSubject(
            @PathVariable Long subjectId,
            @RequestParam(required = false) String department) {
        Subject subject = subjectRepository.findById(subjectId).orElse(null);
        if (subject == null) return ResponseEntity.notFound().build();

        List<Faculty> candidates = facultyRepository.findByActiveTrue();
        if (department != null && !department.isBlank() && !department.equalsIgnoreCase("ALL")) {
            candidates = candidates.stream()
                    .filter(f -> com.easwari.backend.util.DepartmentMatcher.matches(f.getDepartment(), department))
                    .collect(Collectors.toList());
        }

        // Build workload from offerings
        List<SubjectOffering> allOfferings = offeringRepository.findByActiveTrue();
        Map<Long, Integer> loadMap = allocationService.buildOfferingLoadMap(candidates, allOfferings);

        Subject finalSubject = subject;
        List<Map<String, Object>> result = candidates.stream()
                .map(f -> {
                    boolean isEligible = allocationService.isEligibleForSubject(f, finalSubject);
                    int currentLoad = loadMap.getOrDefault(f.getId(), 0);
                    int maxLoad = f.getMaxWeeklyLoad() != null ? f.getMaxWeeklyLoad() : 18;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", f.getId());
                    m.put("name", f.getName());
                    m.put("employeeId", f.getEmployeeId());
                    m.put("department", f.getDepartment());
                    m.put("designation", f.getDesignation() != null ? f.getDesignation() : "");
                    m.put("eligible", isEligible);
                    m.put("currentLoad", currentLoad);
                    m.put("maxWeeklyLoad", maxLoad);
                    m.put("remainingCapacity", Math.max(0, maxLoad - currentLoad));
                    return m;
                })
                .sorted((a, b) -> {
                    // Eligible first, then by remaining capacity descending
                    int eligDiff = Boolean.compare((Boolean) b.get("eligible"), (Boolean) a.get("eligible"));
                    if (eligDiff != 0) return eligDiff;
                    return Integer.compare((Integer) b.get("remainingCapacity"), (Integer) a.get("remainingCapacity"));
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteOffering(@PathVariable Long id) {
        return offeringRepository.findById(id).map(o -> {
            o.setActive(false);
            offeringRepository.save(o);
            return ResponseEntity.ok(Map.of("message", "Subject offering deactivated successfully"));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/sync")
    public ResponseEntity<?> syncOfferings(@RequestParam String department, @RequestParam Integer semester) {
        autoSyncOfferingsIfMissing(department, semester);
        return ResponseEntity.ok(Map.of("message", "Sync completed"));
    }

    private void autoSyncOfferingsIfMissing(String department, Integer semester) {
        try {
            List<Subject> subjects = subjectRepository.findAll().stream()
                    .filter(Subject::isActive)
                    .filter(s -> semester == null || Objects.equals(s.getSemester(), semester))
                    .filter(s -> department == null || department.isEmpty() || department.equalsIgnoreCase("ALL")
                            || com.easwari.backend.util.DepartmentMatcher.matches(s.getDepartment(), department))
                    .collect(Collectors.toList());

            if (subjects.isEmpty()) return;

            // Group subjects by department and semester to ensure sections A..G exist
            Map<String, Set<Integer>> deptSemPairs = new HashMap<>();
            for (Subject s : subjects) {
                if (s.getDepartment() != null && s.getSemester() != null) {
                    deptSemPairs.computeIfAbsent(s.getDepartment(), k -> new HashSet<>()).add(s.getSemester());
                }
            }

            for (Map.Entry<String, Set<Integer>> entry : deptSemPairs.entrySet()) {
                String d = entry.getKey();
                for (Integer sem : entry.getValue()) {
                    ensureDepartmentSections(d, sem);
                }
            }

            List<Batch> allBatches = batchRepository.findAll().stream()
                    .filter(Batch::isActive)
                    .filter(b -> semester == null || Objects.equals(b.getSemester(), semester))
                    .filter(b -> department == null || department.isEmpty() || department.equalsIgnoreCase("ALL")
                            || com.easwari.backend.util.DepartmentMatcher.matches(b.getDepartment(), department))
                    .collect(Collectors.toList());

            // Deduplicate batches by semester and section: exactly ONE batch per section per semester
            Map<String, Batch> uniqueBatches = new LinkedHashMap<>();
            for (Batch b : allBatches) {
                String bKey = (b.getSemester() != null ? b.getSemester() : 0) + "-" 
                        + (b.getSection() != null ? b.getSection().trim().toUpperCase() : b.getBatchName().trim());
                uniqueBatches.putIfAbsent(bKey, b);
            }
            allBatches = new ArrayList<>(uniqueBatches.values());

            List<Faculty> allFaculty = facultyRepository.findAll().stream()
                    .filter(Faculty::isActive)
                    .collect(Collectors.toList());

            List<SubjectOffering> existingOfferings = new ArrayList<>(offeringRepository.findAll());

            for (Subject sub : subjects) {
                List<Batch> subBatches = allBatches.stream()
                        .filter(b -> (sub.getDepartment() == null || com.easwari.backend.util.DepartmentMatcher.matches(b.getDepartment(), sub.getDepartment()))
                                && (sub.getSemester() != null && Objects.equals(b.getSemester(), sub.getSemester())))
                        .sorted(Comparator.comparing(b -> b.getSection() != null ? b.getSection() : b.getBatchName()))
                        .collect(Collectors.toList());

                // Find eligible faculty for this subject
                List<Faculty> eligibleFaculty = allFaculty.stream()
                        .filter(f -> f.getEligibleSubjects() != null && f.getEligibleSubjects().stream().anyMatch(es -> Objects.equals(es.getId(), sub.getId())))
                        .collect(Collectors.toList());

                for (int i = 0; i < subBatches.size(); i++) {
                    Batch b = subBatches.get(i);
                    boolean exists = existingOfferings.stream()
                            .anyMatch(o -> o.isActive()
                                    && o.getBatch() != null && Objects.equals(o.getBatch().getId(), b.getId())
                                    && o.getSubject() != null && Objects.equals(o.getSubject().getId(), sub.getId()));

                    if (!exists) {
                        Faculty assigned = null;
                        if (!eligibleFaculty.isEmpty()) {
                            assigned = eligibleFaculty.get(i % eligibleFaculty.size());
                        }
                        SubjectOffering newOff = SubjectOffering.builder()
                                .batch(b)
                                .subject(sub)
                                .assignedFaculty(assigned)
                                .academicYear(b.getAcademicYear() != null ? b.getAcademicYear() : "2026-2027")
                                .semester(sub.getSemester() != null ? sub.getSemester() : b.getSemester())
                                .active(true)
                                .build();
                        SubjectOffering saved = offeringRepository.save(newOff);
                        existingOfferings.add(saved);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Auto-sync offerings failed: {}", e.getMessage());
        }
    }

    private void ensureDepartmentSections(String department, Integer semester) {
        if (department == null || semester == null || department.equalsIgnoreCase("ALL") || department.contains("[object")) return;
        String canonicalDept = department;
        if (departmentService != null) {
            var info = departmentService.normalizeDepartment(department);
            if (info != null && info.departmentName() != null && !info.departmentName().isBlank()) {
                canonicalDept = info.departmentName();
            }
        }
        char[] sections = {'A', 'B', 'C', 'D', 'E', 'F', 'G'};
        String deptCode = getDeptCode(canonicalDept);
        for (char sec : sections) {
            String secStr = String.valueOf(sec);
            final String finalCanonical = canonicalDept;
            boolean exists = batchRepository.findAll().stream()
                    .anyMatch(b -> b.isActive()
                            && (com.easwari.backend.util.DepartmentMatcher.matches(b.getDepartment(), finalCanonical)
                                || (b.getBatchName() != null && b.getBatchName().equalsIgnoreCase(deptCode + "-" + secStr)))
                            && Objects.equals(b.getSemester(), semester)
                            && secStr.equalsIgnoreCase(b.getSection()));
            if (!exists) {
                batchRepository.save(Batch.builder()
                        .department(canonicalDept)
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
        if (department == null) return "DEPT";
        if (department.contains("Artificial Intelligence & Machine Learning") || department.contains("AI & ML")) return "CSE-AIML";
        if (department.contains("Cyber Security")) return "CSE-CS";
        if (department.equalsIgnoreCase("Computer Science Engineering") || department.equalsIgnoreCase("Computer Science and Engineering")) return "CSE";
        if (department.equalsIgnoreCase("Information Technology")) return "IT";
        if (department.equalsIgnoreCase("Mechanical Engineering")) return "MECH";
        if (department.equalsIgnoreCase("Civil Engineering")) return "CIVIL";
        if (department.equalsIgnoreCase("Electrical & Electronics")) return "EEE";
        if (department.equalsIgnoreCase("Electronics & Communication Engineering")) return "ECE";
        if (department.equalsIgnoreCase("Biomedical Engineering")) return "BME";
        if (department.equalsIgnoreCase("Automobile Engineering")) return "AUTO";
        if (department.equalsIgnoreCase("Robotics and Automation Engineering")) return "ROBOTICS";
        if (department.equalsIgnoreCase("AI and Data Science Engineering")) return "AIDS";
        if (department.contains("Biotechnology")) return "BIOTECH";
        if (department.contains("Business System")) return "CSBS";
        String letters = department.replaceAll("[^A-Z]", "");
        return letters.isEmpty() ? department.substring(0, Math.min(4, department.length())).toUpperCase() : letters;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Long parseLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try { return Long.valueOf(value.toString().trim()); } catch (NumberFormatException e) { return null; }
    }

    private Long parseLongOrNull(Object value) {
        if (value == null) return null;
        String s = value.toString().trim();
        if (s.isEmpty()) return null;
        if (value instanceof Number) return ((Number) value).longValue();
        try { return Long.valueOf(s); } catch (NumberFormatException e) { return null; }
    }

    private Integer parseInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        try { return Integer.valueOf(value.toString().trim()); } catch (NumberFormatException e) { return null; }
    }
}
