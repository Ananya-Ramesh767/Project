package com.odysseus.backend.service;

import com.odysseus.backend.domain.*;
import com.odysseus.backend.repository.*;
import com.odysseus.backend.util.DepartmentMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Intelligent Faculty Allocation Service.
 *
 * Implements a constraint-aware, workload-balanced faculty allocation algorithm.
 * This is the single authoritative source for faculty→section assignment decisions.
 *
 * ALGORITHM:
 * For each (subject, section) pair:
 *   1. Find eligible faculty (explicit eligibility OR specialization match)
 *   2. Filter by department (department subjects → dept faculty; common subjects → wider pool)
 *   3. Filter by availability capacity (currentLoad + subjectHours <= maxWeeklyLoad)
 *   4. Sort by current allocated load (ascending) for even distribution
 *   5. Assign the best candidate, update in-memory load tracker
 *   6. Log every decision with reason
 *
 * KEY DESIGN:
 * - The load tracker is maintained IN-MEMORY across all assignments in one run.
 *   This means if Faculty A gets CSE-A for Computer Networks (3 hrs), their load
 *   is immediately updated, so CSE-B may go to Faculty B instead.
 * - This prevents the "same faculty for all 7 sections" problem.
 * - If only one faculty is eligible for a subject, they WILL be assigned to all sections
 *   (because there is no alternative) — but this is reported as a WARNING.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FacultyAllocationService {

    private final FacultyRepository facultyRepository;
    private final FacultyAvailabilityRepository availabilityRepository;
    private final TimetableAssignmentRepository timetableAssignmentRepository;
    private final SubjectRepository subjectRepository;
    private final CourseDepartmentMappingService courseMappingService;
    private final DepartmentService departmentService;

    /**
     * Represents a single faculty-to-section assignment decision.
     */
    public record AllocationDecision(
            Long batchId,
            String batchName,
            Long subjectId,
            String subjectCode,
            Faculty assignedFaculty,
            String reason,
            boolean isWarning
    ) {}

    /**
     * Result of running the full allocation algorithm for a scope.
     */
    public record AllocationResult(
            List<AllocationDecision> decisions,
            List<String> warnings,
            List<String> infeasibleSubjects,
            Map<Long, Integer> finalLoadMap
    ) {}

    /**
     * Run the allocation algorithm for the given subjects and batches.
     *
     * @param subjects  Active subjects in scope (already filtered by dept/semester)
     * @param batches   Active batches in scope (already filtered by dept/semester)
     * @param department The target department (null = all)
     * @param respectExistingAssignments If true, don't overwrite sections that already have faculty assigned
     * @return AllocationResult with per-section decisions and diagnostics
     */
    public AllocationResult allocate(
            List<Subject> subjects,
            List<Batch> batches,
            String department,
            boolean respectExistingAssignments,
            Map<Long, Long> existingOfferingFaculty  // offeringId -> facultyId (existing assignments)
    ) {
        List<AllocationDecision> decisions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> infeasibleSubjects = new ArrayList<>();

        // Load all active faculty — use dept-filtered or all
        List<Faculty> allFaculty = facultyRepository.findByActiveTrue();

        // Build initial workload map from EXISTING timetable assignments (real scheduled hours)
        Map<Long, Integer> loadMap = buildInitialLoadMap(allFaculty);

        // Build faculty unavailability set: facultyId -> Set<"DAY-P{period}">
        Map<Long, Set<String>> unavailabilityMap = buildUnavailabilityMap(allFaculty);

        // Deduplicate batches by semester and section: exactly ONE batch per section per semester
        Map<String, Batch> uniqueBatches = new LinkedHashMap<>();
        for (Batch b : batches) {
            if (b == null) continue;
            String sec = b.getSection() != null && !b.getSection().trim().isEmpty()
                    ? b.getSection().trim().toUpperCase()
                    : (b.getBatchName() != null ? b.getBatchName().trim() : String.valueOf(b.getId()));
            String key = (b.getSemester() != null ? b.getSemester() : 0) + "-" + sec;
            uniqueBatches.putIfAbsent(key, b);
        }
        List<Batch> effectiveBatches = new ArrayList<>(uniqueBatches.values());

        log.info("[Allocation] Starting allocation for {} subjects × {} sections (dept={})",
                subjects.size(), effectiveBatches.size(), department != null ? department : "ALL");

        for (Subject subject : subjects) {
            boolean isDeptSubject = isDepartmentSubject(subject, department);
            int weeklyHours = computeWeeklyHours(subject);

            // Build candidate pool
            List<Faculty> candidates = buildCandidatePool(subject, allFaculty, department, isDeptSubject);

            if (candidates.isEmpty()) {
                String msg = "Subject [" + subject.getSubjectCode() + " – " + subject.getSubjectName()
                        + "] has NO eligible faculty" + (department != null ? " in " + shortDept(department) : "")
                        + ". Add faculty eligibility in Faculty Master → Review & Confirm.";
                warnings.add(msg);
                infeasibleSubjects.add(subject.getSubjectCode());
                log.warn("[Allocation] {} — skipping all {} sections", msg, effectiveBatches.size());
                continue;
            }

            log.info("[Allocation] Subject [{}] — {} eligible candidate(s): {}",
                    subject.getSubjectCode(),
                    candidates.size(),
                    candidates.stream().map(Faculty::getName).collect(Collectors.joining(", ")));

            // Allocate per section with cross-section uniqueness for the same subject
            Set<Long> assignedToThisSubject = new HashSet<>();
            for (Batch batch : effectiveBatches) {
                // Filter batches to match subject semester if subject has explicit semester
                if (subject.getSemester() != null && !subject.getSemester().equals(batch.getSemester())) {
                    continue;
                }

                // Check if already assigned and we should respect it
                Long existingFacultyId = existingOfferingFaculty.get(encodeBatchSubjectKey(batch.getId(), subject.getId()));
                if (respectExistingAssignments && existingFacultyId != null) {
                    Faculty existing = allFaculty.stream()
                            .filter(f -> f.getId().equals(existingFacultyId) && f.isEligibleForTeaching()).findFirst().orElse(null);
                    if (existing != null) {
                        assignedToThisSubject.add(existing.getId());
                        decisions.add(new AllocationDecision(
                                batch.getId(), batch.getBatchName(),
                                subject.getId(), subject.getSubjectCode(),
                                existing, "Existing assignment preserved", false));
                        log.debug("[Allocation] [{}] [{}] → {} (preserved existing)",
                                subject.getSubjectCode(), batch.getBatchName(), existing.getName());
                        continue;
                    }
                }

                // Pick best candidate: eligible, available capacity, lowest load, NOT already assigned to this subject
                Faculty selected = selectBestCandidate(candidates, loadMap, unavailabilityMap, weeklyHours, subject, batch, assignedToThisSubject);

                if (selected == null) {
                    // All candidates are over capacity — fall back to least loaded regardless
                    selected = candidates.stream()
                            .min(Comparator.comparingInt(f -> loadMap.getOrDefault(f.getId(), 0)))
                            .orElse(null);

                    if (selected != null) {
                        String warnMsg = "Faculty [" + selected.getName() + "] assigned to ["
                                + subject.getSubjectCode() + "] section [" + batch.getBatchName()
                                + "] but is at/near workload limit. Consider adding more eligible faculty.";
                        warnings.add(warnMsg);
                        log.warn("[Allocation] OVERLOAD WARNING: {}", warnMsg);
                    }
                }

                if (selected == null) {
                    String msg = "No faculty available for [" + subject.getSubjectCode() + "] section ["
                            + batch.getBatchName() + "] — all candidates at workload limit.";
                    warnings.add(msg);
                    log.warn("[Allocation] {}", msg);
                    continue;
                }

                assignedToThisSubject.add(selected.getId());
                // Update in-memory load tracker immediately so next section benefits
                loadMap.merge(selected.getId(), weeklyHours, Integer::sum);

                String reason = buildReason(selected, loadMap, weeklyHours, candidates.size());
                decisions.add(new AllocationDecision(
                        batch.getId(), batch.getBatchName(),
                        subject.getId(), subject.getSubjectCode(),
                        selected, reason, false));

                log.info("[Allocation] [{}] [{}] → {} (load: {}/{}, reason: {})",
                        subject.getSubjectCode(), batch.getBatchName(),
                        selected.getName(),
                        loadMap.getOrDefault(selected.getId(), 0),
                        selected.getMaxWeeklyLoad() != null ? selected.getMaxWeeklyLoad() : 18,
                        reason);
            }

            // Warn if all sections got the same faculty (only one candidate existed)
            if (candidates.size() == 1) {
                long distinctFaculty = decisions.stream()
                        .filter(d -> d.subjectId().equals(subject.getId()))
                        .map(d -> d.assignedFaculty().getId())
                        .distinct().count();
                if (distinctFaculty == 1) {
                    warnings.add("Subject [" + subject.getSubjectCode() + "]: Only 1 eligible faculty available — "
                            + candidates.get(0).getName() + " assigned to all "
                            + batches.size() + " sections. Add more eligible faculty for better distribution.");
                }
            }
        }

        log.info("[Allocation] Complete: {} decisions, {} warnings, {} infeasible subjects",
                decisions.size(), warnings.size(), infeasibleSubjects.size());

        return new AllocationResult(decisions, warnings, infeasibleSubjects, loadMap);
    }

    // ── Candidate Selection ────────────────────────────────────────────────────

    public boolean isSpecialTestSubject(Subject subject) {
        if (subject == null) return false;
        String name = subject.getSubjectName() != null ? subject.getSubjectName().toLowerCase() : "";
        String code = subject.getSubjectCode() != null ? subject.getSubjectCode().toLowerCase() : "";
        return name.contains("aptitude") || name.contains("technical test")
                || name.contains("counselling") || name.contains("counseling")
                || name.contains("library") || name.contains("assessment")
                || name.contains("leetcode") || name.contains("leet code")
                || code.contains("apt") || code.contains("tec") || code.contains("cou") || code.contains("lib")
                || code.contains("lee");
    }

    /**
     * Build the candidate pool for a subject using generic course prefix to department mapping.
     * Flow per Master Prompt Section 6:
     * Subject -> Course Code -> Extract Prefix -> Find Approved Department Mapping -> Resolve Department Key -> Retrieve Faculty with Matching Department Key
     */
    private List<Faculty> buildCandidatePool(Subject subject, List<Faculty> allFaculty,
                                              String department, boolean isDeptSubject) {
        // Exclude HODs, lab technicians, and non-teaching staff strictly from teaching candidate pool
        List<Faculty> eligibleFaculty = allFaculty.stream()
                .filter(Faculty::isEligibleForTeaching)
                .collect(Collectors.toList());

        // 1. Resolve department key via Course Code Prefix -> Department Mapping
        String targetDeptKey = null;
        if (courseMappingService != null && subject.getSubjectCode() != null) {
            String prefix = courseMappingService.extractCoursePrefix(subject.getSubjectCode());
            if (prefix != null) {
                Optional<CourseDepartmentMapping> mappingOpt = courseMappingService.resolveMapping(prefix);
                if (mappingOpt.isPresent() && ("APPROVED".equalsIgnoreCase(mappingOpt.get().getApprovalStatus()) || "MAPPED".equalsIgnoreCase(mappingOpt.get().getStatus()))
                        && !"UNMAPPED".equalsIgnoreCase(mappingOpt.get().getDepartmentKey())) {
                    targetDeptKey = mappingOpt.get().getDepartmentKey();
                }
            }
        }

        // Common/special tests fallback to curriculum department
        if (targetDeptKey == null && isSpecialTestSubject(subject)) {
            targetDeptKey = (department != null && !department.isBlank() && !department.equalsIgnoreCase("ALL"))
                    ? (departmentService != null ? departmentService.normalizeDepartment(department).departmentKey() : "CSE")
                    : "CSE";
        }

        // Fallback to subject's normalized department if explicit
        if (targetDeptKey == null && departmentService != null) {
            String subDept = subject.getFacultyDepartment() != null && !subject.getFacultyDepartment().isBlank()
                    ? subject.getFacultyDepartment() : subject.getDepartment();
            if (subDept != null && !subDept.isBlank()) {
                targetDeptKey = departmentService.normalizeDepartment(subDept).departmentKey();
            }
        }

        final String finalTargetDeptKey = targetDeptKey;

        // 2. Filter faculty whose normalized departmentKey matches the subject's departmentKey
        if (finalTargetDeptKey != null && !"UNMAPPED".equalsIgnoreCase(finalTargetDeptKey)) {
            List<Faculty> matched = eligibleFaculty.stream()
                    .filter(f -> {
                        String fKey = f.getDepartmentKey();
                        String fDept = f.getDepartment();
                        return DepartmentMatcher.matches(fKey, finalTargetDeptKey)
                                || DepartmentMatcher.matches(fDept, finalTargetDeptKey);
                    })
                    .collect(Collectors.toList());

            if (!matched.isEmpty()) {
                return matched;
            }
        }

        // Fallback: Check explicit eligibility
        List<Faculty> explicitMatches = eligibleFaculty.stream()
                .filter(f -> f.getEligibleSubjects() != null && f.getEligibleSubjects().stream().anyMatch(s -> Objects.equals(s.getId(), subject.getId())))
                .collect(Collectors.toList());
        if (!explicitMatches.isEmpty()) {
            return explicitMatches;
        }

        // Unmapped or no matching department faculty -> return empty candidate pool
        return Collections.emptyList();
    }

    /**
     * Select the best faculty candidate for a (subject, section) pair.
     * Criteria: eligible, non-HOD, within workload capacity, lowest current load.
     */
    private Faculty selectBestCandidate(List<Faculty> candidates, Map<Long, Integer> loadMap,
                                         Map<Long, Set<String>> unavailabilityMap,
                                         int weeklyHours, Subject subject, Batch batch,
                                         Set<Long> alreadyAssignedToSubject) {
        // 1. Prefer candidate NOT already assigned to this subject across sections
        Optional<Faculty> unassignedToSubject = candidates.stream()
                .filter(Faculty::isEligibleForTeaching)
                .filter(f -> alreadyAssignedToSubject == null || !alreadyAssignedToSubject.contains(f.getId()))
                .filter(f -> {
                    int currentLoad = loadMap.getOrDefault(f.getId(), 0);
                    int maxLoad = f.getMaxWeeklyLoad() != null ? f.getMaxWeeklyLoad() : 18;
                    return (currentLoad + weeklyHours) <= maxLoad;
                })
                .min(Comparator.comparingInt(f -> loadMap.getOrDefault(f.getId(), 0)));

        if (unassignedToSubject.isPresent()) {
            return unassignedToSubject.get();
        }

        // 2. Fallback only if candidate pool is smaller than section count
        return candidates.stream()
                .filter(Faculty::isEligibleForTeaching)
                .filter(f -> {
                    int currentLoad = loadMap.getOrDefault(f.getId(), 0);
                    int maxLoad = f.getMaxWeeklyLoad() != null ? f.getMaxWeeklyLoad() : 18;
                    return (currentLoad + weeklyHours) <= maxLoad;
                })
                .min(Comparator.comparingInt(f -> loadMap.getOrDefault(f.getId(), 0)))
                .orElse(null);
    }

    /**
     * Check if a faculty member is eligible for a subject.
     * Evaluates explicit eligibility and generic course prefix to department mapping.
     */
    public boolean isEligibleForSubject(Faculty f, Subject subject) {
        if (f == null || subject == null) return false;
        if (!f.isEligibleForTeaching()) return false;

        // 1. Explicit eligibility (primary)
        if (f.getEligibleSubjects() != null) {
            boolean explicit = f.getEligibleSubjects().stream()
                    .anyMatch(s -> Objects.equals(s.getId(), subject.getId()));
            if (explicit) return true;
        }

        // 2. Resolve subject departmentKey via Course Code prefix
        String subjectDeptKey = null;
        if (courseMappingService != null && subject.getSubjectCode() != null) {
            String prefix = courseMappingService.extractCoursePrefix(subject.getSubjectCode());
            if (prefix != null) {
                Optional<CourseDepartmentMapping> mappingOpt = courseMappingService.resolveMapping(prefix);
                if (mappingOpt.isPresent() && "APPROVED".equalsIgnoreCase(mappingOpt.get().getApprovalStatus())
                        && !"UNMAPPED".equalsIgnoreCase(mappingOpt.get().getDepartmentKey())) {
                    subjectDeptKey = mappingOpt.get().getDepartmentKey();
                }
            }
        }

        if (subjectDeptKey == null && departmentService != null) {
            String subDept = subject.getFacultyDepartment() != null && !subject.getFacultyDepartment().isBlank()
                    ? subject.getFacultyDepartment() : subject.getDepartment();
            if (subDept != null && !subDept.isBlank()) {
                subjectDeptKey = departmentService.normalizeDepartment(subDept).departmentKey();
            }
        }

        if (subjectDeptKey == null || "UNMAPPED".equalsIgnoreCase(subjectDeptKey)) {
            return false;
        }

        String facultyDeptKey = f.getDepartmentKey();
        if (facultyDeptKey == null || facultyDeptKey.isBlank()) {
            if (departmentService != null) {
                facultyDeptKey = departmentService.normalizeDepartment(f.getDepartment()).departmentKey();
            }
        }

        // Default eligibility rule: subject.departmentKey === faculty.departmentKey
        return facultyDeptKey != null && facultyDeptKey.equalsIgnoreCase(subjectDeptKey);
    }

    // ── Workload Tracking ──────────────────────────────────────────────────────

    /**
     * Build initial load map from the EXISTING timetable assignments (real scheduled periods).
     * This reflects the ACTUAL current scheduled workload, not just offering counts.
     */
    public Map<Long, Integer> buildInitialLoadMap(List<Faculty> allFaculty) {
        Map<Long, Integer> loadMap = new HashMap<>();
        List<TimetableAssignment> existing = timetableAssignmentRepository.findByActiveTrue();
        for (Faculty f : allFaculty) {
            int scheduled = existing.stream()
                    .filter(a -> a.getFaculty() != null && Objects.equals(a.getFaculty().getId(), f.getId())
                            && ("ACADEMIC".equals(a.getActivityType()) || "LAB".equals(a.getActivityType())))
                    .mapToInt(a -> a.getDurationPeriods() != null ? a.getDurationPeriods() : 1)
                    .sum();
            loadMap.put(f.getId(), scheduled);
        }
        return loadMap;
    }

    /**
     * Build workload from offerings (before timetable is generated).
     * Used for pre-generation validation when no timetable assignments exist yet.
     */
    public Map<Long, Integer> buildOfferingLoadMap(List<Faculty> allFaculty,
                                                    List<com.odysseus.backend.domain.SubjectOffering> offerings) {
        Map<Long, Integer> loadMap = new HashMap<>();
        for (com.odysseus.backend.domain.SubjectOffering o : offerings) {
            if (o.getAssignedFaculty() == null) continue;
            int hrs = computeWeeklyHours(o.getSubject());
            loadMap.merge(o.getAssignedFaculty().getId(), hrs, Integer::sum);
        }
        return loadMap;
    }

    // ── Unavailability ─────────────────────────────────────────────────────────

    private Map<Long, Set<String>> buildUnavailabilityMap(List<Faculty> allFaculty) {
        Map<Long, Set<String>> map = new HashMap<>();
        for (Faculty f : allFaculty) {
            List<FacultyAvailability> avList = availabilityRepository.findByFacultyId(f.getId());
            Set<String> unavail = avList.stream()
                    .filter(av -> !av.isAvailable())
                    .map(av -> av.getDayOfWeek().toUpperCase() + "-P" + av.getPeriodNumber())
                    .collect(Collectors.toSet());
            map.put(f.getId(), unavail);
        }
        return map;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * A subject belongs to a department if its department field matches the target.
     * Common/interdisciplinary subjects (dept = "General", "Common", null, or mismatching) are not dept-specific.
     */
    private boolean isDepartmentSubject(Subject subject, String targetDepartment) {
        if (targetDepartment == null || targetDepartment.isBlank()) return false;
        String subDept = subject.getDepartment();
        if (subDept == null || subDept.isBlank()) return false;
        if ("General".equalsIgnoreCase(subDept) || "Common".equalsIgnoreCase(subDept)) return false;
        return DepartmentMatcher.matches(subDept, targetDepartment);
    }

    public int computeWeeklyHours(Subject subject) {
        int L = subject.getWeeklyLecturePeriods() != null ? subject.getWeeklyLecturePeriods() : 0;
        int T = subject.getWeeklyTutorialPeriods() != null ? subject.getWeeklyTutorialPeriods() : 0;
        int P = subject.getWeeklyPracticalPeriods() != null ? subject.getWeeklyPracticalPeriods() : 0;
        int total = L + T + P;
        if (total > 0) return total;
        double calc = subject.getCalculatedWeeklyHours();
        if (calc > 0) return (int) Math.ceil(calc);
        return 1; // safety minimum
    }

    private String buildReason(Faculty f, Map<Long, Integer> loadMap, int weeklyHours, int candidateCount) {
        int newLoad = loadMap.getOrDefault(f.getId(), 0);
        int prevLoad = newLoad - weeklyHours;
        int maxLoad = f.getMaxWeeklyLoad() != null ? f.getMaxWeeklyLoad() : 18;
        return String.format("eligible, workload %d→%d/%d hrs/wk, %d candidate(s) considered",
                prevLoad, newLoad, maxLoad, candidateCount);
    }

    /** Encode a batch+subject combination as a lookup key */
    public Long encodeBatchSubjectKey(Long batchId, Long subjectId) {
        return batchId * 100_000L + subjectId;
    }

    private String shortDept(String department) {
        if (department == null) return "";
        if (department.toLowerCase().contains("computer science")) return "CSE";
        if (department.toLowerCase().contains("information technology")) return "IT";
        if (department.toLowerCase().contains("electronics")) return "ECE";
        if (department.toLowerCase().contains("mechanical")) return "MECH";
        return department.length() > 20 ? department.substring(0, 20) + "…" : department;
    }
}
