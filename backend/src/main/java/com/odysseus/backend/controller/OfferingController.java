package com.odysseus.backend.controller;

import com.odysseus.backend.domain.Batch;
import com.odysseus.backend.domain.Faculty;
import com.odysseus.backend.domain.Subject;
import com.odysseus.backend.domain.SubjectOffering;
import com.odysseus.backend.repository.BatchRepository;
import com.odysseus.backend.repository.FacultyRepository;
import com.odysseus.backend.repository.SubjectOfferingRepository;
import com.odysseus.backend.repository.SubjectRepository;
import com.odysseus.backend.service.FacultyAllocationService;
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

    @GetMapping
    public ResponseEntity<List<SubjectOffering>> getAllOfferings(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Integer semester) {
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
                if (o.getBatch() != null && com.odysseus.backend.util.DepartmentMatcher.matches(o.getBatch().getDepartment(), department)) return true;
                if (o.getSubject() != null && com.odysseus.backend.util.DepartmentMatcher.matches(o.getSubject().getDepartment(), department)) return true;
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

        Integer semester = parseInteger(payload.get("semester"));

        SubjectOffering offering = SubjectOffering.builder()
                .batch(batch)
                .subject(subject)
                .assignedFaculty(assignedFaculty)
                .academicYear((String) payload.get("academicYear"))
                .semester(semester != null ? semester : batch.getSemester())
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

        // 1. Load scope data
        List<Subject> subjects = subjectRepository.findAll().stream()
                .filter(Subject::isActive)
                .filter(s -> {
                    if (semester != null && !Objects.equals(s.getSemester(), semester)) return false;
                    if (department != null && !department.trim().isEmpty() && !department.equalsIgnoreCase("ALL")) {
                        return com.odysseus.backend.util.DepartmentMatcher.matches(s.getDepartment(), department);
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
                        return com.odysseus.backend.util.DepartmentMatcher.matches(b.getDepartment(), department);
                    }
                    return true;
                })
                .sorted(Comparator.comparing(Batch::getBatchName))
                .collect(Collectors.toList());

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
                if (batch != null && subject != null) {
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
                    .filter(f -> com.odysseus.backend.util.DepartmentMatcher.matches(f.getDepartment(), department))
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
