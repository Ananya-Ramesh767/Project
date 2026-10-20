package com.odysseus.backend.controller;

import com.odysseus.backend.domain.Batch;
import com.odysseus.backend.repository.BatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/batches")
@RequiredArgsConstructor
public class BatchController {

    private final BatchRepository batchRepository;

    @GetMapping
    public ResponseEntity<List<Batch>> getAllBatches(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Integer semester) {
        List<Batch> batches = batchRepository.findByActiveTrue();
        if (semester != null) {
            batches = batches.stream().filter(b -> java.util.Objects.equals(b.getSemester(), semester)).collect(java.util.stream.Collectors.toList());
        }
        if (department != null && !department.trim().isEmpty() && !department.equalsIgnoreCase("ALL")) {
            batches = batches.stream().filter(b -> com.odysseus.backend.util.DepartmentMatcher.matches(b.getDepartment(), department)).collect(java.util.stream.Collectors.toList());
        }
        return ResponseEntity.ok(batches);
    }

    @PostMapping("/generate-sections")
    public ResponseEntity<?> generateSections(@RequestBody Map<String, Object> req) {
        String department = (String) req.getOrDefault("department", "Computer Science Engineering");
        Integer semester = req.get("semester") != null ? Integer.parseInt(req.get("semester").toString()) : 1;
        int sectionCount = req.get("sectionCount") != null ? Integer.parseInt(req.get("sectionCount").toString()) : 1;
        int strength = req.get("strength") != null ? Integer.parseInt(req.get("strength").toString()) : 60;
        String academicYear = (String) req.getOrDefault("academicYear", "2026-2027");

        if (strength > 70) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Maximum student strength is 70 students per section/classroom allocation. " +
                            "Please split into additional sections or adjust student strength to 70 or below."
            ));
        }

        String deptCode = department;
        if (department.contains("Artificial Intelligence & Machine Learning") || department.contains("AI & ML")) deptCode = "CSE-AIML";
        else if (department.contains("Cyber Security")) deptCode = "CSE-CS";
        else if (department.equalsIgnoreCase("Computer Science Engineering")) deptCode = "CSE";
        else if (department.equalsIgnoreCase("Information Technology")) deptCode = "IT";
        else if (department.equalsIgnoreCase("Mechanical Engineering")) deptCode = "MECH";
        else if (department.equalsIgnoreCase("Civil Engineering")) deptCode = "CIVIL";
        else if (department.equalsIgnoreCase("Electrical & Electronics")) deptCode = "EEE";
        else if (department.equalsIgnoreCase("Electronics & Communication Engineering")) deptCode = "ECE";
        else if (department.equalsIgnoreCase("Biomedical Engineering")) deptCode = "BME";
        else if (department.equalsIgnoreCase("Automobile Engineering")) deptCode = "AUTO";
        else if (department.equalsIgnoreCase("Robotics and Automation Engineering")) deptCode = "ROBOTICS";
        else if (department.equalsIgnoreCase("AI and Data Science Engineering")) deptCode = "AIDS";
        else if (department.contains("Biotechnology")) deptCode = "BIOTECH";
        else if (department.contains("Business System")) deptCode = "CSBS";
        else if (department.length() > 6) {
            deptCode = department.replaceAll("[^A-Z]", "");
            if (deptCode.isEmpty()) deptCode = department.substring(0, Math.min(4, department.length())).toUpperCase();
        }

        java.util.List<Batch> created = new java.util.ArrayList<>();
        for (int i = 0; i < sectionCount; i++) {
            char secChar = (char) ('A' + i);
            String batchName = deptCode + "-" + secChar;
            Batch batch = batchRepository.findByDepartmentAndSemesterAndSection(department, semester, String.valueOf(secChar))
                    .orElse(Batch.builder()
                            .department(department)
                            .semester(semester)
                            .section(String.valueOf(secChar))
                            .batchName(batchName)
                            .strength(strength)
                            .academicYear(academicYear)
                            .active(true)
                            .build());
            batch.setBatchName(batchName);
            batch.setStrength(strength);
            batch.setActive(true);
            created.add(batchRepository.save(batch));
        }
        return ResponseEntity.ok(created);
    }

    @PostMapping
    public ResponseEntity<?> createBatch(@RequestBody Batch batch) {
        if (batch.getStrength() != null && batch.getStrength() > 70) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Maximum student strength is 70 students per section. Current value (" + batch.getStrength() + ") exceeds the institutional capacity."
            ));
        }
        Batch saved = batchRepository.save(batch);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateBatch(@PathVariable Long id, @RequestBody Batch batch) {
        if (!batchRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        if (batch.getStrength() != null && batch.getStrength() > 70) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Maximum student strength is 70 students per section. Current value (" + batch.getStrength() + ") exceeds the institutional capacity."
            ));
        }
        batch.setId(id);
        Batch saved = batchRepository.save(batch);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBatch(@PathVariable Long id) {
        return batchRepository.findById(id).map(b -> {
            b.setActive(false);
            batchRepository.save(b);
            return ResponseEntity.ok(Map.of("message", "Batch deactivated successfully"));
        }).orElse(ResponseEntity.notFound().build());
    }
}
