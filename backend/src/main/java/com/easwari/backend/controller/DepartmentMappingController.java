package com.easwari.backend.controller;

import com.easwari.backend.domain.CourseDepartmentMapping;
import com.easwari.backend.domain.Department;
import com.easwari.backend.service.CourseDepartmentMappingService;
import com.easwari.backend.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/department-mappings")
@RequiredArgsConstructor
@Slf4j
public class DepartmentMappingController {

    private final CourseDepartmentMappingService mappingService;
    private final DepartmentService departmentService;

    @GetMapping
    public ResponseEntity<List<CourseDepartmentMapping>> getMappings(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {

        // Auto-discover any new subject prefixes
        List<CourseDepartmentMapping> list = mappingService.syncMappingsFromSubjects();

        if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
            list = list.stream()
                    .filter(m -> m.getStatus() != null && m.getStatus().equalsIgnoreCase(status.trim()))
                    .collect(Collectors.toList());
        }

        if (search != null && !search.isBlank()) {
            String q = search.trim().toLowerCase();
            list = list.stream()
                    .filter(m -> (m.getCoursePrefix() != null && m.getCoursePrefix().toLowerCase().contains(q))
                            || (m.getDepartmentKey() != null && m.getDepartmentKey().toLowerCase().contains(q))
                            || (m.getDepartmentName() != null && m.getDepartmentName().toLowerCase().contains(q)))
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok(list);
    }

    @PostMapping
    public ResponseEntity<?> createMapping(@RequestBody Map<String, Object> req) {
        try {
            String prefix = (String) req.get("coursePrefix");
            String departmentKey = (String) req.get("departmentKey");
            String status = req.containsKey("status") ? (String) req.get("status") : "MAPPED";
            boolean approve = req.containsKey("approvalStatus")
                    && "APPROVED".equalsIgnoreCase(String.valueOf(req.get("approvalStatus")));

            CourseDepartmentMapping mapping = mappingService.saveOrUpdateMapping(prefix, departmentKey, status, approve);
            return ResponseEntity.ok(mapping);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateMapping(@PathVariable Long id, @RequestBody Map<String, Object> req) {
        try {
            String prefix = (String) req.get("coursePrefix");
            String departmentKey = (String) req.get("departmentKey");
            String status = req.containsKey("status") ? (String) req.get("status") : "MAPPED";
            boolean approve = req.containsKey("approvalStatus")
                    && "APPROVED".equalsIgnoreCase(String.valueOf(req.get("approvalStatus")));

            CourseDepartmentMapping mapping = mappingService.saveOrUpdateMapping(prefix, departmentKey, status, approve);
            return ResponseEntity.ok(mapping);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @RequestMapping(value = "/{id}/approve", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<?> approveMapping(@PathVariable Long id) {
        try {
            CourseDepartmentMapping mapping = mappingService.approveMapping(id);
            return ResponseEntity.ok(mapping);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMapping(@PathVariable Long id) {
        mappingService.deleteMapping(id);
        return ResponseEntity.ok(Map.of("message", "Mapping deleted successfully"));
    }

    @GetMapping("/departments")
    public ResponseEntity<List<Department>> getDepartments() {
        return ResponseEntity.ok(departmentService.getAllActiveDepartments());
    }

    @PostMapping("/departments")
    public ResponseEntity<?> createDepartment(@RequestBody Map<String, String> req) {
        try {
            String key = req.get("departmentKey");
            String name = req.get("departmentName");
            String aliases = req.get("aliases");
            Department dept = departmentService.createOrUpdateDepartment(key, name, aliases);
            return ResponseEntity.ok(dept);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
