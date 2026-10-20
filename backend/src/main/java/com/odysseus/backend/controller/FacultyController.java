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
            @RequestParam(required = false) Integer semester) {

        // Get subjects for this department
        List<Subject> subjects = subjectRepository.findAll().stream()
                .filter(Subject::isActive)
                .filter(s -> department.isEmpty() || com.odysseus.backend.util.DepartmentMatcher.matches(s.getDepartment(), department))
                .filter(s -> semester == null || Objects.equals(s.getSemester(), semester))
                .sorted(Comparator.comparing(Subject::getSubjectCode))
                .collect(Collectors.toList());

        // Get all active faculty
        List<Faculty> allActiveFaculty = facultyRepository.findByActiveTrue();

        // Department-relevant faculty = those whose department matches OR who already have eligible subjects in this dept
        List<Faculty> deptFaculty = allActiveFaculty.stream()
                .filter(f -> department.isEmpty() || com.odysseus.backend.util.DepartmentMatcher.matches(f.getDepartment(), department)
                        || f.getEligibleSubjects().stream().anyMatch(s -> com.odysseus.backend.util.DepartmentMatcher.matches(s.getDepartment(), department)))
                .collect(Collectors.toList());

        // Active batches for section assignment
        List<Batch> batches = batchRepository.findAll().stream()
                .filter(Batch::isActive)
                .filter(b -> department.isEmpty() || com.odysseus.backend.util.DepartmentMatcher.matches(b.getDepartment(), department))
                .filter(b -> semester == null || Objects.equals(b.getSemester(), semester))
                .sorted(Comparator.comparing(Batch::getBatchName))
                .collect(Collectors.toList());

        List<Map<String, Object>> batchList = batches.stream().map(b -> {
            Map<String, Object> bm = new LinkedHashMap<>();
            bm.put("id", b.getId());
            bm.put("batchName", b.getBatchName());
            bm.put("section", b.getSection() != null ? b.getSection() : "");
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

        // Build proposals
        List<Map<String, Object>> proposals = new ArrayList<>();

        for (Subject subject : subjects) {
            // Eligible faculty = those who have this subject in eligibleSubjects OR whose specialization/subjectsHandled matches
            List<Faculty> eligible = deptFaculty.stream()
                    .filter(f -> f.getEligibleSubjects().stream().anyMatch(s -> Objects.equals(s.getId(), subject.getId()))
                            || matchesSpecializationOrSubjects(f, subject))
                    .collect(Collectors.toList());

            List<Batch> relevantSubjectBatches = batches.stream()
                    .filter(b -> subject.getSemester() == null || Objects.equals(b.getSemester(), subject.getSemester()))
                    .collect(Collectors.toList());
            if (relevantSubjectBatches.isEmpty()) relevantSubjectBatches = batches;

            int weeklyPeriods = allocationService.computeWeeklyHours(subject);
            int totalSectionDemand = relevantSubjectBatches.size() * weeklyPeriods;
            // A faculty member can realistically teach at most 16 hours of one subject
            int minNeeded = (int) Math.ceil((double) totalSectionDemand / 16.0);
            int requiredCount = Math.max(subject.getRequiredFacultyCount(), Math.max(1, minNeeded));
            if (!eligible.isEmpty()) {
                requiredCount = Math.min(requiredCount, eligible.size());
            }

            // Suggested faculty: up to requiredCount faculty with lowest current workload
            List<Long> suggestedFacultyIds = eligible.stream()
                    .sorted(Comparator.comparingInt(f -> loadTracker.getOrDefault(f.getId(), 0)))
                    .limit(requiredCount)
                    .map(Faculty::getId)
                    .collect(Collectors.toList());

            // Suggested per-section faculty distribution (evenly spreads sections across suggested faculty)
            Map<Long, Long> suggestedSectionFaculty = new LinkedHashMap<>();
            if (!suggestedFacultyIds.isEmpty()) {
                for (int i = 0; i < relevantSubjectBatches.size(); i++) {
                    Long fid = suggestedFacultyIds.get(i % suggestedFacultyIds.size());
                    suggestedSectionFaculty.put(relevantSubjectBatches.get(i).getId(), fid);
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
            for (Batch b : batches) {
                offeringRepository.findAll().stream()
                        .filter(o -> o.isActive() && o.getBatch() != null && Objects.equals(o.getBatch().getId(), b.getId())
                                && o.getSubject() != null && Objects.equals(o.getSubject().getId(), subject.getId())
                                && o.getAssignedFaculty() != null)
                        .findFirst()
                        .ifPresent(o -> existingSectionFaculty.put(b.getId(), o.getAssignedFaculty().getId()));
            }

            Map<String, Object> proposal = new LinkedHashMap<>();
            proposal.put("subjectId", subject.getId());
            proposal.put("subjectCode", subject.getSubjectCode());
            proposal.put("subjectName", subject.getSubjectName());
            proposal.put("subjectType", subject.getSubjectType());
            proposal.put("department", subject.getDepartment());
            proposal.put("semester", subject.getSemester());
            proposal.put("weeklyHours", subject.getCalculatedWeeklyHours());
            proposal.put("requiredFacultyCount", requiredCount);
            proposal.put("eligibleFaculty", eligibleList);
            proposal.put("suggestedFacultyIds", suggestedFacultyIds);
            proposal.put("suggestedSectionFaculty", suggestedSectionFaculty);
            proposal.put("requiresAdminInput", eligible.isEmpty());
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
            Map<Long, Integer> sectionLoadMap = allocationService.buildInitialLoadMap(confirmedFacultyList);
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
            @RequestParam(value = "department", defaultValue = "Computer Science Engineering") String department) {
        try {
            List<Map<String, Object>> facultyList = pdfExtractionService.extractFacultyFromFile(file, department);
            return ResponseEntity.ok(Map.of(
                    "totalExtracted", facultyList.size(),
                    "preview", facultyList,
                    "fileName", file.getOriginalFilename() != null ? file.getOriginalFilename() : ""
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Unable to extract faculty document: " + e.getMessage()));
        }
    }

    @PostMapping("/batch-save")
    public ResponseEntity<?> batchSaveFaculty(@RequestBody List<Map<String, Object>> facultyList) {
        List<Faculty> savedList = new ArrayList<>();
        List<String> updatedIds = new ArrayList<>();

        for (Map<String, Object> map : facultyList) {
            String empId = (String) map.get("employeeId");
            if (empId == null || empId.trim().isEmpty()) continue;

            Optional<Faculty> existing = facultyRepository.findByEmployeeId(empId);
            Faculty f;
            if (existing.isPresent()) {
                f = existing.get();
                updatedIds.add(empId);
            } else {
                f = new Faculty();
                f.setEmployeeId(empId);
                f.setEligibleSubjects(new HashSet<>());
            }
            f.setName((String) map.get("name"));
            f.setEmail((String) map.get("email"));

            String dept = (String) map.get("department");
            if (dept == null || dept.trim().isEmpty()) {
                dept = "Computer Science Engineering";
            }
            f.setDepartment(dept);

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
        if (department == null) return "DEPT";
        if (department.contains("Artificial Intelligence & Machine Learning") || department.contains("AI & ML")) return "CSE-AIML";
        if (department.contains("Cyber Security")) return "CSE-CS";
        if (department.equalsIgnoreCase("Computer Science Engineering")) return "CSE";
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
}
