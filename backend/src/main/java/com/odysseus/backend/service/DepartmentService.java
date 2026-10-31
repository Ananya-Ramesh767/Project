package com.odysseus.backend.service;

import com.odysseus.backend.domain.Department;
import com.odysseus.backend.domain.Faculty;
import com.odysseus.backend.repository.DepartmentRepository;
import com.odysseus.backend.repository.FacultyRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Generic Department Registry & Normalization Service.
 *
 * Implements Institutional 21 Departments:
 * 1. CSE, 2. IT, 3. ECE, 4. EEE, 5. MECH, 6. CIVIL, 7. AIDS, 8. AIML, 9. CSBS, 10. CSD,
 * 11. CYBER, 12. AUTO, 13. BME, 14. BT, 15. RA, 16. MATH, 17. PHY, 18. CHEM, 19. ENG, 20. MBA, 21. MCA.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final FacultyRepository facultyRepository;

    public record DepartmentInfo(Long id, String departmentKey, String departmentName) {}

    @PostConstruct
    @Transactional
    public void initDefaultDepartments() {
        log.info("[DepartmentService] Ensuring all 21 institutional departments are registered and active...");

        registerOrUpdateCanonical("CSE", "Computer Science and Engineering", "Computer Science Engineering, CSE Department, Comp Sci, Computer Science & Engineering, CSE, CSE1");
        registerOrUpdateCanonical("IT", "Information Technology", "Information Technology, IT Department, Information Tech, IT");
        registerOrUpdateCanonical("ECE", "Electronics and Communication Engineering", "Electronics & Communication Engineering, Electronics and Communication, ECE Department, ECE");
        registerOrUpdateCanonical("EEE", "Electrical and Electronics Engineering", "Electrical & Electronics, Electrical & Electronics Engineering, Electrical and Electronics, EEE Department, EEE, EE");
        registerOrUpdateCanonical("MECH", "Mechanical Engineering", "Mechanical Engineering, Mechanical, Mech, MECH Department, ME");
        registerOrUpdateCanonical("CIVIL", "Civil Engineering", "Civil Engineering, Civil, CIVIL Department, CE");
        registerOrUpdateCanonical("AIDS", "Artificial Intelligence and Data Science", "AI and Data Science Engineering, Artificial Intelligence and Data Science, Artificial Intelligence & Data Science, AIDS, AI&DS");
        registerOrUpdateCanonical("AIML", "Computer Science & Engineering (Artificial Intelligence & Machine Learning)", "AIML, CSE-AIML, CSE AI/ML, Artificial Intelligence & Machine Learning, Computer Science & Engineering (Artificial Intelligence & Machine Learning), Computer Science and Engineering (Artificial Intelligence and Machine Learning), Artificial Intelligence and Machine Learning");
        registerOrUpdateCanonical("CSBS", "Computer Science and Business Systems", "CSBS, CSB, Computer Science & Business Systems, Computer Science and Business Systems, Computer Science and Business");
        registerOrUpdateCanonical("CSD", "Computer Science and Design", "CSD, Computer Science & Design, Computer Science and Design, Computer Science and Design (CSD)");
        registerOrUpdateCanonical("CYBER", "Computer Science & Engineering (Cyber Security)", "CYBER, Cyber Security, CSE Cyber Security, CSE-Cyber, Computer Science & Engineering (Cyber Security), Computer Science and Engineering (Cyber Security), CSE (Cyber Security)");
        registerOrUpdateCanonical("AUTO", "Automobile Engineering", "Automobile Engineering, Automobile, Auto, AE");
        registerOrUpdateCanonical("BME", "Biomedical Engineering", "Biomedical Engineering, Biomedical, BME, BE");
        registerOrUpdateCanonical("BT", "Biotechnology", "Biotechnology, B.Tech. Biotechnology, Biotech, BT, BIOTEC");
        registerOrUpdateCanonical("RA", "Robotics and Automation Engineering", "Robotics and Automation Engineering, Robotics and Automation, RA, RAE");
        registerOrUpdateCanonical("MATH", "Mathematics", "Mathematics, Maths, Math, Department of Mathematics, MATHEM");
        registerOrUpdateCanonical("PHY", "Physics", "Physics, Department of Physics, PHYSIC");
        registerOrUpdateCanonical("CHEM", "Chemistry", "Chemistry, Department of Chemistry, CHEMIS");
        registerOrUpdateCanonical("ENG", "English", "English, Technical English, Humanities, Department of English, ENGLIS");
        registerOrUpdateCanonical("MBA", "Master of Business Administration", "MBA, Master of Business Administration, Management Studies, Master of Business Administration (MBA)");
        registerOrUpdateCanonical("MCA", "Master of Computer Applications", "MCA, Master of Computer Applications, Master of Computer Applications (MCA)");

        cleanupMalformedDepartments();
        syncFacultyDepartmentKeysAndExclusions();

        log.info("[DepartmentService] Total active departments in registry: {}", departmentRepository.count());
    }

    private void registerOrUpdateCanonical(String key, String name, String aliases) {
        Department dept = departmentRepository.findByDepartmentKeyIgnoreCase(key).orElse(null);
        if (dept == null) {
            dept = Department.builder()
                    .departmentKey(key)
                    .departmentName(name)
                    .aliases(aliases)
                    .active(true)
                    .build();
        } else {
            dept.setDepartmentName(name);
            dept.setAliases(aliases);
            dept.setActive(true);
        }
        departmentRepository.save(dept);
    }

    private void cleanupMalformedDepartments() {
        List<String> malformedKeys = List.of(
                "CSE1", "CSD(", "MBA(", "MCA(", "AE", "BE", "BIOTEC", "CE", "CHEMIS",
                "CSB", "EE", "ENGLIS", "MATHEM", "ME", "PHYSIC", "RAE"
        );
        for (String mKey : malformedKeys) {
            departmentRepository.findByDepartmentKeyIgnoreCase(mKey).ifPresent(d -> {
                log.info("[DepartmentService] Removing malformed/legacy department entry: {}", mKey);
                departmentRepository.delete(d);
            });
        }
    }

    public void syncFacultyDepartmentKeysAndExclusions() {
        if (facultyRepository == null) return;
        List<Faculty> all = facultyRepository.findAll();
        boolean changed = false;

        Map<String, String> keyMap = new HashMap<>();
        keyMap.put("CSE1", "CSE");
        keyMap.put("AE", "AUTO");
        keyMap.put("BE", "BME");
        keyMap.put("BIOTEC", "BT");
        keyMap.put("CE", "CIVIL");
        keyMap.put("CHEMIS", "CHEM");
        keyMap.put("CSB", "CSBS");
        keyMap.put("CSD(", "CSD");
        keyMap.put("EE", "EEE");
        keyMap.put("ENGLIS", "ENG");
        keyMap.put("MATHEM", "MATH");
        keyMap.put("MBA(", "MBA");
        keyMap.put("MCA(", "MCA");
        keyMap.put("ME", "MECH");
        keyMap.put("PHYSIC", "PHY");
        keyMap.put("RAE", "RA");

        for (Faculty f : all) {
            boolean facultyModified = false;

            // 1. Normalize departmentKey
            String curKey = f.getDepartmentKey();
            if (curKey != null && keyMap.containsKey(curKey)) {
                f.setDepartmentKey(keyMap.get(curKey));
                facultyModified = true;
            } else if (curKey == null || curKey.isBlank()) {
                DepartmentInfo info = normalizeDepartment(f.getDepartment());
                if (!"UNMAPPED".equalsIgnoreCase(info.departmentKey())) {
                    f.setDepartmentKey(info.departmentKey());
                    facultyModified = true;
                }
            }

            // 2. Exclude Principal Dr. Deiva Sundari P
            if (f.isPrincipal()) {
                if (!"PRINCIPAL".equalsIgnoreCase(f.getCategory()) || Boolean.TRUE.equals(f.getEligibleForTeaching())) {
                    f.setCategory("PRINCIPAL");
                    f.setEligibleForTeaching(false);
                    facultyModified = true;
                }
            }

            // 3. Exclude all HODs
            if (f.isHod()) {
                if (!"HOD".equalsIgnoreCase(f.getCategory()) || Boolean.TRUE.equals(f.getEligibleForTeaching())) {
                    f.setCategory("HOD");
                    f.setHod(true);
                    f.setEligibleForTeaching(false);
                    facultyModified = true;
                }
            }

            if (facultyModified) {
                facultyRepository.save(f);
                changed = true;
            }
        }
        if (changed) {
            log.info("[DepartmentService] Synchronized faculty department keys and Principal/HOD teaching exclusions.");
        }
    }

    /**
     * Normalizes an arbitrary department string or heading into a stable DepartmentInfo.
     */
    @Transactional
    public DepartmentInfo normalizeDepartment(String rawInput) {
        if (rawInput == null || rawInput.trim().isEmpty()) {
            return new DepartmentInfo(null, "UNMAPPED", "Unmapped Department");
        }

        String cleaned = cleanDepartmentString(rawInput);
        if (cleaned.isEmpty()) {
            return new DepartmentInfo(null, "UNMAPPED", "Unmapped Department");
        }

        List<Department> all = departmentRepository.findByActiveTrue();

        // 1. Direct match on key
        for (Department d : all) {
            if (d.getDepartmentKey().equalsIgnoreCase(cleaned)) {
                return new DepartmentInfo(d.getId(), d.getDepartmentKey(), d.getDepartmentName());
            }
        }

        // 2. Direct match on name
        for (Department d : all) {
            if (d.getDepartmentName().equalsIgnoreCase(cleaned)) {
                return new DepartmentInfo(d.getId(), d.getDepartmentKey(), d.getDepartmentName());
            }
        }

        // 3. Match against aliases
        String normalizedCleaned = normalizeForComparison(cleaned);
        for (Department d : all) {
            if (d.getAliases() != null) {
                String[] aliasArr = d.getAliases().split(",");
                for (String a : aliasArr) {
                    if (normalizeForComparison(a.trim()).equals(normalizedCleaned)) {
                        return new DepartmentInfo(d.getId(), d.getDepartmentKey(), d.getDepartmentName());
                    }
                }
            }
        }

        // 4. Substring / fuzzy heuristic on known variants
        for (Department d : all) {
            String normName = normalizeForComparison(d.getDepartmentName());
            if (normalizedCleaned.equals(normName) || normalizedCleaned.contains(normName) || normName.contains(normalizedCleaned)) {
                if (normName.length() >= 5 && normalizedCleaned.length() >= 5) {
                    return new DepartmentInfo(d.getId(), d.getDepartmentKey(), d.getDepartmentName());
                }
            }
        }

        // 5. Dynamic registration: New department discovered in PDF or upload!
        String derivedKey = deriveKeyFromName(cleaned);
        String uniqueKey = derivedKey;
        int counter = 1;
        while (departmentRepository.findByDepartmentKeyIgnoreCase(uniqueKey).isPresent()) {
            uniqueKey = derivedKey + counter++;
        }

        log.info("[DepartmentService] Dynamically registering new department: [{}] with key [{}]", cleaned, uniqueKey);
        Department newDept = Department.builder()
                .departmentKey(uniqueKey)
                .departmentName(cleaned)
                .aliases(cleaned + ", " + uniqueKey)
                .active(true)
                .build();
        Department saved = departmentRepository.save(newDept);
        return new DepartmentInfo(saved.getId(), saved.getDepartmentKey(), saved.getDepartmentName());
    }

    public Department findByKey(String key) {
        if (key == null) return null;
        if (departmentRepository.count() == 0) {
            initDefaultDepartments();
        }
        return departmentRepository.findByDepartmentKeyIgnoreCase(key.trim()).orElse(null);
    }

    public List<Department> getAllActiveDepartments() {
        if (departmentRepository.count() < 21) {
            initDefaultDepartments();
        }
        return departmentRepository.findAllByOrderByDepartmentKeyAsc();
    }

    @Transactional
    public Department createOrUpdateDepartment(String key, String name, String aliases) {
        if (key == null || key.isBlank() || name == null || name.isBlank()) {
            throw new IllegalArgumentException("Department key and name must not be blank.");
        }
        String cleanKey = key.trim().toUpperCase();
        Department dept = departmentRepository.findByDepartmentKeyIgnoreCase(cleanKey)
                .orElse(Department.builder().departmentKey(cleanKey).active(true).build());
        dept.setDepartmentName(name.trim());
        if (aliases != null) {
            dept.setAliases(aliases.trim());
        }
        return departmentRepository.save(dept);
    }

    public static String cleanDepartmentString(String s) {
        if (s == null) return "";
        String t = s.trim();
        t = t.replaceAll("(?i)^(department\\s+of|dept\\.?\\s+of|school\\s+of)\\s+", "");
        t = t.replaceAll("(?i)\\s+(faculty|faculty\\s+members|faculty\\s+list|department|dept\\.)$", "");
        return t.trim();
    }

    public static String normalizeForComparison(String s) {
        if (s == null) return "";
        return s.toLowerCase()
                .replaceAll("&", "and")
                .replaceAll("[^a-z0-9]", "");
    }

    public static String deriveKeyFromName(String name) {
        if (name == null || name.isBlank()) return "DEPT";
        String cleaned = cleanDepartmentString(name);
        String[] words = cleaned.split("[\\s_\\-]+");
        if (words.length == 1) {
            return words[0].toUpperCase().substring(0, Math.min(6, words[0].length()));
        }
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            String lw = w.toLowerCase();
            if (lw.equals("and") || lw.equals("of") || lw.equals("in") || lw.equals("&") || lw.equals("for")) continue;
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)));
            }
        }
        String key = sb.toString();
        return key.length() >= 2 ? key : cleaned.substring(0, Math.min(4, cleaned.length())).toUpperCase();
    }
}
