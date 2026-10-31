package com.odysseus.backend.controller;

import com.odysseus.backend.domain.TimetableEntry;
import com.odysseus.backend.domain.TimetableJob;
import com.odysseus.backend.dto.DataReadinessResult;
import com.odysseus.backend.dto.SchedulerInputContractDto;
import com.odysseus.backend.repository.TimetableEntryRepository;
import com.odysseus.backend.repository.TimetableJobRepository;
import com.odysseus.backend.service.DataReadinessService;
import com.odysseus.backend.service.SchedulerDataService;
import com.odysseus.backend.service.TimetableSolverService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/scheduler")
@RequiredArgsConstructor
public class SchedulerController {

    private final SchedulerDataService schedulerDataService;
    private final DataReadinessService readinessService;
    private final TimetableSolverService solverService;
    private final TimetableJobRepository jobRepository;
    private final TimetableEntryRepository entryRepository;

    /**
     * GET /api/scheduler/input
     * Returns the validated scheduler input contract JSON.
     */
    @GetMapping("/input")
    public ResponseEntity<SchedulerInputContractDto> getSchedulerInput() {
        return ResponseEntity.ok(schedulerDataService.buildSchedulerInputPayload());
    }

    /**
     * GET /api/scheduler/readiness
     * Returns the data readiness report for the given scope (non-destructive, no job created).
     * Used by the frontend to show a live pre-generation status panel.
     */
    @GetMapping("/readiness")
    public ResponseEntity<?> getReadiness(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false) String academicYear) {
        DataReadinessResult readiness = readinessService.checkDataReadiness(department, semester, academicYear);
        return ResponseEntity.ok(readiness);
    }

    /**
     * POST /api/scheduler/generate
     * Validates data readiness, creates a solver job, and kicks off async generation.
     *
     * Optional scope params: ?department=Computer Science Engineering&semester=3
     */
    @PostMapping("/generate")
    public ResponseEntity<?> triggerTimetableGeneration(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Integer semester) {
        // 1. Scope-aware data readiness check
        DataReadinessResult readiness = readinessService.checkDataReadiness(department, semester, null);

        if (!readiness.isReady()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", "VALIDATION_FAILED",
                    "message", "Cannot generate timetable: " + readiness.getBlockingIssues().size()
                            + " blocking issue(s). See readiness report.",
                    "readiness", readiness
            ));
        }

        // 2. Create a new job
        String jobId = UUID.randomUUID().toString();
        TimetableJob job = TimetableJob.builder()
                .id(jobId)
                .status("QUEUED")
                .department(department)
                .semester(semester)
                .message("Timetable generation queued. Solver will start shortly.")
                .createdAt(LocalDateTime.now())
                .build();
        jobRepository.save(job);

        // 3. Start async solver
        solverService.solveAsync(jobId, department, semester);

        // 4. Return immediately
        return ResponseEntity.ok(Map.of(
                "jobId", jobId,
                "status", "QUEUED",
                "message", "Timetable generation started"
        ));
    }


    /**
     * GET /api/scheduler/jobs/{jobId}
     * Returns the current status of a solver job.
     */
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<?> getJobStatus(@PathVariable String jobId) {
        return jobRepository.findById(jobId)
                .map(job -> ResponseEntity.ok(Map.of(
                        "jobId", job.getId(),
                        "status", job.getStatus(),
                        "message", job.getMessage() != null ? job.getMessage() : "",
                        "totalEntries", job.getTotalEntries(),
                        "createdAt", job.getCreatedAt() != null ? job.getCreatedAt().toString() : "",
                        "completedAt", job.getCompletedAt() != null ? job.getCompletedAt().toString() : ""
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/timetables/{jobId}
     * Returns all timetable entries for a completed job.
     */
    @GetMapping("/timetables/{jobId}")
    public ResponseEntity<?> getTimetable(@PathVariable String jobId) {
        return jobRepository.findById(jobId)
                .map(job -> {
                    if (!"COMPLETED".equals(job.getStatus()) && !"INFEASIBLE".equals(job.getStatus())) {
                        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                                "error", "Job is not yet complete",
                                "status", job.getStatus()
                        ));
                    }
                    List<TimetableEntry> entries = entryRepository
                            .findByJobIdOrderByDayOfWeekAscPeriodNumberAsc(jobId);
                    return ResponseEntity.ok(Map.of(
                            "jobId", jobId,
                            "status", job.getStatus(),
                            "totalEntries", entries.size(),
                            "entries", entries
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
