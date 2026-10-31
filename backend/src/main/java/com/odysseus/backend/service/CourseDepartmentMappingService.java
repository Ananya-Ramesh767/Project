package com.odysseus.backend.service;

import com.odysseus.backend.domain.CourseDepartmentMapping;
import com.odysseus.backend.domain.Department;
import com.odysseus.backend.domain.Subject;
import com.odysseus.backend.repository.CourseDepartmentMappingRepository;
import com.odysseus.backend.repository.SubjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Course Code Prefix to Department Mapping Service.
 *
 * Implements Sections 3, 4, 5, 6 & 7:
 * - Robust course code prefix extraction (e.g. 2321CSC301T -> CSC, 2321MES101J -> MES)
 * - Persistent registry connecting course prefix to actual department
 * - Discovery priority:
 *    1. Administrator-approved mapping (never overwritten)
 *    2. Verified curriculum subject data
 *    3. Existing institutional mapping
 *    4. Otherwise mark as UNMAPPED
 * - Enforces reuse: multiple subjects with the same prefix share one mapping
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CourseDepartmentMappingService {

    private final CourseDepartmentMappingRepository mappingRepository;
    private final DepartmentService departmentService;
    private final SubjectRepository subjectRepository;

    // Pattern A: Scheme/regulation prefix digits (e.g. 2321, 1915, 23) followed by 2-5 letters and numbers/suffix
    // Example: 2321CSC301T -> CSC, 2321MAB301T -> MAB, 2321MES101J -> MES
    private static final Pattern REGULATION_CODE_PATTERN = Pattern.compile("^\\d{2,4}([A-Za-z]{2,5})\\d{2,4}[A-Za-z0-9]*$");

    // Pattern B: Direct letter-prefix followed by course number (e.g. CS8351 -> CS, MA3354 -> MA, CSC301 -> CSC)
    private static final Pattern DIRECT_CODE_PATTERN = Pattern.compile("^([A-Za-z]{2,5})\\d{2,4}[A-Za-z0-9]*$");

    /**
     * Extracts the course prefix from a course code.
     * E.g. 2321CSC301T -> CSC, 2321MAB301T -> MAB, 2321MES101J -> MES, 2321SDA101L -> SDA
     */
    public String extractCoursePrefix(String courseCode) {
        if (courseCode == null) return null;
        String code = courseCode.trim().replaceAll("\\s+", "");
        if (code.isEmpty()) return null;

        Matcher regMatcher = REGULATION_CODE_PATTERN.matcher(code);
        if (regMatcher.matches()) {
            return regMatcher.group(1).toUpperCase();
        }

        Matcher directMatcher = DIRECT_CODE_PATTERN.matcher(code);
        if (directMatcher.matches()) {
            return directMatcher.group(1).toUpperCase();
        }

        // Fallback: If code contains letters, extract consecutive letter sequence
        Matcher anyLetters = Pattern.compile("([A-Za-z]{2,5})").matcher(code);
        if (anyLetters.find()) {
            return anyLetters.group(1).toUpperCase();
        }

        return null;
    }

    /**
     * Resolves the department mapping for a course prefix according to Section 4 priority:
     * 1. Existing administrator-approved mapping
     * 2. Existing database mapping record
     * 3. Null / Unmapped if not found
     */
    public Optional<CourseDepartmentMapping> resolveMapping(String prefix) {
        if (prefix == null || prefix.isBlank()) return Optional.empty();
        String cleanPrefix = prefix.trim().toUpperCase();
        Optional<CourseDepartmentMapping> existing = mappingRepository.findByCoursePrefixIgnoreCase(cleanPrefix);
        if (existing.isPresent()) {
            CourseDepartmentMapping m = existing.get();
            if (!"UNMAPPED".equalsIgnoreCase(m.getDepartmentKey()) && !"CONFLICT".equalsIgnoreCase(m.getStatus())) {
                return existing;
            }
        }

        // Try institutional prefix rule resolution
        Department d = resolveDepartmentForPrefix(cleanPrefix);
        if (d != null) {
            CourseDepartmentMapping autoMapping = existing.orElse(CourseDepartmentMapping.builder().coursePrefix(cleanPrefix).build());
            autoMapping.setDepartmentId(d.getId());
            autoMapping.setDepartmentKey(d.getDepartmentKey());
            autoMapping.setDepartmentName(d.getDepartmentName());
            autoMapping.setSource("INSTITUTIONAL_RULE");
            autoMapping.setConfidence("HIGH");
            autoMapping.setStatus("MAPPED");
            autoMapping.setApprovalStatus("APPROVED");
            try {
                autoMapping = mappingRepository.save(autoMapping);
            } catch (Exception ignored) {}
            return Optional.of(autoMapping);
        }

        return existing;
    }

    public Department resolveDepartmentForPrefix(String pfx) {
        if (pfx == null) return null;
        String clean = pfx.trim().toUpperCase();

        if (clean.equals("MAB") || clean.equals("MAT") || clean.equals("MA")) {
            return departmentService.findByKey("MATH");
        }
        if (clean.equals("CSC") || clean.equals("CSE") || clean.equals("CSD") || clean.equals("CSBS")
                || clean.equals("AIML") || clean.equals("CS")) {
            return departmentService.findByKey("CSE");
        }
        if (clean.equals("CYB") || clean.equals("CHM") || clean.equals("CHE") || clean.equals("CH")) {
            return departmentService.findByKey("CHEM");
        }
        if (clean.equals("MES") || clean.equals("MEC") || clean.equals("ME")) {
            return departmentService.findByKey("MECH");
        }
        if (clean.equals("EC") || clean.equals("ECE") || clean.equals("ESD") || clean.equals("CSS")) {
            return departmentService.findByKey("ECE");
        }
        if (clean.equals("EE") || clean.equals("EEE")) {
            return departmentService.findByKey("EEE");
        }
        if (clean.equals("PHY") || clean.equals("PHB") || clean.equals("PH")) {
            return departmentService.findByKey("PHY");
        }
        if (clean.equals("ENG") || clean.equals("ENB") || clean.equals("EN")) {
            return departmentService.findByKey("ENG");
        }
        if (clean.equals("BME") || clean.equals("BM")) {
            return departmentService.findByKey("BME");
        }
        if (clean.equals("BT") || clean.equals("BIOTECH")) {
            return departmentService.findByKey("BT");
        }
        if (clean.equals("AUTO") || clean.equals("AT")) {
            return departmentService.findByKey("AUTO");
        }
        if (clean.equals("CIVIL") || clean.equals("CE")) {
            return departmentService.findByKey("CIVIL");
        }
        if (clean.equals("AIDS") || clean.equals("AD")) {
            return departmentService.findByKey("AIDS");
        }
        if (clean.equals("APT") || clean.equals("TEC") || clean.equals("COU") || clean.equals("LIB")
                || clean.equals("NPT") || clean.equals("SDA") || clean.equals("LEE")) {
            return departmentService.findByKey("CSE");
        }
        return null;
    }

    /**
     * Synchronizes and discovers mappings across all existing subjects.
     * Extracts prefixes, registers unmapped prefixes for administrator review,
     * and auto-resolves high-confidence mappings from verified subject metadata.
     * Never overwrites an approved mapping!
     */
    @Transactional
    public List<CourseDepartmentMapping> syncMappingsFromSubjects() {
        List<Subject> subjects = subjectRepository.findAll();
        Map<String, List<Subject>> prefixToSubjects = new LinkedHashMap<>();

        for (Subject s : subjects) {
            String prefix = extractCoursePrefix(s.getSubjectCode());
            if (prefix != null && !prefix.isBlank()) {
                prefixToSubjects.computeIfAbsent(prefix, k -> new ArrayList<>()).add(s);
            }
        }

        for (Map.Entry<String, List<Subject>> entry : prefixToSubjects.entrySet()) {
            String prefix = entry.getKey();
            List<Subject> subList = entry.getValue();

            Optional<CourseDepartmentMapping> existingOpt = mappingRepository.findByCoursePrefixIgnoreCase(prefix);
            if (existingOpt.isPresent()) {
                CourseDepartmentMapping existing = existingOpt.get();
                // Priority 1: Never overwrite administrator-approved mapping!
                if ("APPROVED".equalsIgnoreCase(existing.getApprovalStatus())) {
                    continue;
                }

                // If currently UNMAPPED and we now have explicit institutional department data in subjects:
                if ("UNMAPPED".equalsIgnoreCase(existing.getStatus())) {
                    tryInferFromSubjects(existing, subList);
                }
                continue;
            }

            // New prefix discovered: create mapping record
            CourseDepartmentMapping newMapping = CourseDepartmentMapping.builder()
                    .coursePrefix(prefix)
                    .status("UNMAPPED")
                    .approvalStatus("PENDING")
                    .departmentKey("UNMAPPED")
                    .departmentName("Unmapped Department")
                    .source("DISCOVERED")
                    .confidence("LOW")
                    .build();

            tryInferFromSubjects(newMapping, subList);
            mappingRepository.save(newMapping);
            log.info("[CourseMapping] Created mapping record for prefix [{}]: status={}", prefix, newMapping.getStatus());
        }

        return mappingRepository.findAllByOrderByCoursePrefixAsc();
    }

    private void tryInferFromSubjects(CourseDepartmentMapping mapping, List<Subject> subList) {
        String pfx = mapping.getCoursePrefix() != null ? mapping.getCoursePrefix().toUpperCase() : "";

        // Institutional curriculum rules
        Department matchedDept = resolveDepartmentForPrefix(pfx);
        if (matchedDept != null) {
            mapping.setDepartmentId(matchedDept.getId());
            mapping.setDepartmentKey(matchedDept.getDepartmentKey());
            mapping.setDepartmentName(matchedDept.getDepartmentName());
            mapping.setSource("INSTITUTIONAL_RULE");
            mapping.setConfidence("HIGH");
            mapping.setStatus("MAPPED");
            mapping.setApprovalStatus("APPROVED");
            return;
        }

        // Collect explicit department declarations from subjects with this prefix
        Map<String, Integer> deptVotes = new HashMap<>();
        for (Subject s : subList) {
            String dept = s.getFacultyDepartment();
            if (dept == null || dept.isBlank()) {
                dept = s.getDepartment();
            }
            if (dept != null && !dept.isBlank() && !dept.equalsIgnoreCase("ALL")) {
                DepartmentService.DepartmentInfo info = departmentService.normalizeDepartment(dept);
                if (!"UNMAPPED".equalsIgnoreCase(info.departmentKey())) {
                    deptVotes.merge(info.departmentKey(), 1, Integer::sum);
                }
            }
        }

        // If unanimous explicit department evidence is found:
        if (deptVotes.size() == 1) {
            String deptKey = deptVotes.keySet().iterator().next();
            Department d = departmentService.findByKey(deptKey);
            if (d != null) {
                mapping.setDepartmentId(d.getId());
                mapping.setDepartmentKey(d.getDepartmentKey());
                mapping.setDepartmentName(d.getDepartmentName());
                mapping.setSource("VERIFIED_CURRICULUM");
                mapping.setConfidence("HIGH");
                mapping.setStatus("MAPPED");
                mapping.setApprovalStatus("APPROVED");
            }
        } else if (deptVotes.size() > 1) {
            mapping.setStatus("CONFLICT");
            mapping.setConfidence("LOW");
            mapping.setSource("CONFLICTING_DATA");
        }
    }

    /**
     * Administrator creates or approves a department mapping for a prefix.
     */
    @Transactional
    public CourseDepartmentMapping saveOrUpdateMapping(String prefix, String departmentKey, String status, boolean approve) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("Course prefix cannot be empty.");
        }
        String cleanPrefix = prefix.trim().toUpperCase();

        Department dept = departmentService.findByKey(departmentKey);
        if (dept == null) {
            // Try normalizing
            DepartmentService.DepartmentInfo info = departmentService.normalizeDepartment(departmentKey);
            dept = departmentService.findByKey(info.departmentKey());
        }

        if (dept == null && !"UNMAPPED".equalsIgnoreCase(departmentKey)) {
            throw new IllegalArgumentException("Department with key '" + departmentKey + "' not found in registry.");
        }

        CourseDepartmentMapping mapping = mappingRepository.findByCoursePrefixIgnoreCase(cleanPrefix)
                .orElse(CourseDepartmentMapping.builder().coursePrefix(cleanPrefix).build());

        if (dept != null) {
            mapping.setDepartmentId(dept.getId());
            mapping.setDepartmentKey(dept.getDepartmentKey());
            mapping.setDepartmentName(dept.getDepartmentName());
            mapping.setStatus(status != null ? status : "MAPPED");
        } else {
            mapping.setDepartmentId(null);
            mapping.setDepartmentKey("UNMAPPED");
            mapping.setDepartmentName("Unmapped Department");
            mapping.setStatus("UNMAPPED");
        }

        mapping.setSource("ADMIN_APPROVED");
        mapping.setConfidence("HIGH");
        mapping.setApprovalStatus(approve ? "APPROVED" : "PENDING");

        CourseDepartmentMapping saved = mappingRepository.save(mapping);
        log.info("[CourseMapping] Admin saved mapping: [{}] -> [{}] (status={}, approval={})",
                cleanPrefix, saved.getDepartmentKey(), saved.getStatus(), saved.getApprovalStatus());
        return saved;
    }

    @Transactional
    public CourseDepartmentMapping approveMapping(Long id) {
        CourseDepartmentMapping mapping = mappingRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Mapping with id " + id + " not found."));
        if ("UNMAPPED".equalsIgnoreCase(mapping.getDepartmentKey())) {
            throw new IllegalStateException("Cannot approve an unmapped prefix. Please select a department first.");
        }
        mapping.setApprovalStatus("APPROVED");
        mapping.setStatus("MAPPED");
        mapping.setSource("ADMIN_APPROVED");
        return mappingRepository.save(mapping);
    }

    @Transactional
    public void deleteMapping(Long id) {
        mappingRepository.deleteById(id);
    }

    public List<CourseDepartmentMapping> getAllMappings() {
        return mappingRepository.findAllByOrderByCoursePrefixAsc();
    }
}
