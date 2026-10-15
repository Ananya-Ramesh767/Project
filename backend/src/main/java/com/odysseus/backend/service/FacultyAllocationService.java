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

        log.info("[Allocation] Starting allocation for {} subjects × {} sections (dept={})",
                subjects.size(), batches.size(), department != null ? department : "ALL");

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
                log.warn("[Allocation] {} — skipping all {} sections", msg, batches.size());
                continue;
            }

            log.info("[Allocation] Subject [{}] — {} eligible candidate(s): {}",
                    subject.getSubjectCode(),
                    candidates.size(),
                    candidates.stream().map(Faculty::getName).collect(Collectors.joining(", ")));

            // Allocate per section
            for (Batch batch : batches) {
                // Filter batches to match subject semester if subject has explicit semester
                if (subject.getSemester() != null && !subject.getSemester().equals(batch.getSemester())) {
                    continue;
                }

                // Check if already assigned and we should respect it
                Long existingFacultyId = existingOfferingFaculty.get(encodeBatchSubjectKey(batch.getId(), subject.getId()));
                if (respectExistingAssignments && existingFacultyId != null) {
                    Faculty existing = allFaculty.stream()
                            .filter(f -> f.getId().equals(existingFacultyId)).findFirst().orElse(null);
                    if (existing != null) {
                        decisions.add(new AllocationDecision(
                                batch.getId(), batch.getBatchName(),
                                subject.getId(), subject.getSubjectCode(),
                                existing, "Existing assignment preserved", false));
                        log.debug("[Allocation] [{}] [{}] → {} (preserved existing)",
                                subject.getSubjectCode(), batch.getBatchName(), existing.getName());
                        continue;
                    }
                }

                // Pick best candidate: eligible, available capacity, lowest load
                Faculty selected = selectBestCandidate(candidates, loadMap, unavailabilityMap, weeklyHours, subject, batch);

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

    /**
     * Build the candidate pool for a subject.
     * For department subjects: prefer faculty from the same department.
     * For common/special subjects: use all faculty with eligibility.
     */
    private List<Faculty> buildCandidatePool(Subject subject, List<Faculty> allFaculty,
                                              String department, boolean isDeptSubject) {
        List<Faculty> pool;

        if (isDeptSubject && department != null) {
            // Try department-specific eligible faculty first
            pool = allFaculty.stream()
                    .filter(f -> isEligibleForSubject(f, subject))
                    .filter(f -> DepartmentMatcher.matches(f.getDepartment(), department))
                    .collect(Collectors.toList());

            // If no dept-specific eligible faculty, broaden to all eligible
            if (pool.isEmpty()) {
                pool = allFaculty.stream()
                        .filter(f -> isEligibleForSubject(f, subject))
                        .collect(Collectors.toList());
            }
        } else {
            // Common/non-department subject — all eligible faculty
            pool = allFaculty.stream()
                    .filter(f -> isEligibleForSubject(f, subject))
                    .collect(Collectors.toList());
        }

        return pool;
    }

    /**
     * Select the best faculty candidate for a (subject, section) pair.
     * Criteria: eligible, within workload capacity, lowest current load.
     */
    private Faculty selectBestCandidate(List<Faculty> candidates, Map<Long, Integer> loadMap,
                                         Map<Long, Set<String>> unavailabilityMap,
                                         int weeklyHours, Subject subject, Batch batch) {
        return candidates.stream()
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
     * Eligibility = explicit eligible subjects list OR specialization/subjectsHandled text match.
     */
    public boolean isEligibleForSubject(Faculty f, Subject subject) {
        if (f == null || subject == null) return false;

        // 1. Explicit eligibility (primary — set via Faculty Master)
        if (f.getEligibleSubjects() != null) {
            boolean explicit = f.getEligibleSubjects().stream()
                    .anyMatch(s -> Objects.equals(s.getId(), subject.getId()));
            if (explicit) return true;
        }

        // 2. Text-based match from subjectsHandled / specialization (secondary — from PDF upload)
        String spec = (f.getSpecialization() != null ? f.getSpecialization() : "").toLowerCase();
        String handled = (f.getSubjectsHandled() != null ? f.getSubjectsHandled() : "").toLowerCase();
        String combined = spec + " " + handled;

        if (combined.isBlank()) return false;

        String code = subject.getSubjectCode() != null ? subject.getSubjectCode().toLowerCase() : "";
        String name = subject.getSubjectName() != null ? subject.getSubjectName().toLowerCase() : "";

        if (!code.isEmpty() && combined.contains(code)) return true;
        if (!name.isEmpty() && combined.contains(name)) return true;

        // 3. Keyword match on significant words (≥4 chars, not stop-words)
        Set<String> stopWords = Set.of("intro", "introduction", "fundamentals", "applied",
                "engineering", "science", "theory", "laboratory", "advanced",
                "course", "basics", "studies", "principles");
        for (String word : name.split("[^a-z0-9]+")) {
            if (word.length() >= 4 && !stopWords.contains(word) && combined.contains(word)) {
                return true;
            }
        }
        return false;
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
