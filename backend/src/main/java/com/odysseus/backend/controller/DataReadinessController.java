package com.odysseus.backend.controller;

import com.odysseus.backend.dto.DataReadinessResult;
import com.odysseus.backend.service.DataReadinessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/readiness")
@RequiredArgsConstructor
public class DataReadinessController {

    private final DataReadinessService readinessService;

    /**
     * GET /api/admin/readiness
     *
     * Optional scope parameters:
     *   ?department=Computer Science Engineering
     *   &semester=3
     *   &academicYear=2026-2027
     *
     * When scope is provided, only validates data relevant to that scope.
     * When no scope is provided, performs a global structural check.
     */
    @GetMapping
    public ResponseEntity<DataReadinessResult> checkReadiness(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false) String academicYear) {
        return ResponseEntity.ok(readinessService.checkDataReadiness(department, semester, academicYear));
    }
}
