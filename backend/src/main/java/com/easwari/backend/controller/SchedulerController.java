package com.easwari.backend.controller;

import com.easwari.backend.domain.TimetableEntry;
import com.easwari.backend.domain.TimetableJob;
import com.easwari.backend.dto.DataReadinessResult;
import com.easwari.backend.dto.SchedulerInputContractDto;
import com.easwari.backend.repository.TimetableEntryRepository;
import com.easwari.backend.repository.TimetableJobRepository;
import com.easwari.backend.service.DataReadinessService;
import com.easwari.backend.service.SchedulerDataService;
import com.easwari.backend.service.TimetableSolverService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

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
     * Optional scope params: ?department=Computer Science Engineering&semester=3 or ?dept=...&sem=...
     */
    @PostMapping("/generate")
    public ResponseEntity<?> triggerTimetableGeneration(
            @RequestParam(required = false) String dept,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Integer sem,
            @RequestParam(required = false) Integer semester) {
        String targetDept = (dept != null && !dept.isBlank()) ? dept : department;
        Integer targetSem = (sem != null) ? sem : semester;

        // 1. Scope-aware data readiness check
        DataReadinessResult readiness = readinessService.checkDataReadiness(targetDept, targetSem, null);

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
                .department(targetDept)
                .semester(targetSem)
                .message("Timetable generation queued. Solver will start shortly.")
                .createdAt(LocalDateTime.now())
                .build();
        jobRepository.save(job);

        // 3. Start async solver
        solverService.solveAsync(jobId, targetDept, targetSem);

        // 4. Return immediately
        return ResponseEntity.ok(Map.of(
                "jobId", jobId,
                "status", "QUEUED",
                "message", "Timetable generation started"
        ));
    }

    /**
     * GET /api/scheduler/jobs/latest
     * Returns the latest job for a given department and semester scope.
     */
    @GetMapping("/jobs/latest")
    public ResponseEntity<?> getLatestJob(
            @RequestParam(required = false) String dept,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Integer sem,
            @RequestParam(required = false) Integer semester) {
        final String targetDept = (dept != null && !dept.isBlank()) ? dept : department;
        final Integer targetSem = (sem != null) ? sem : semester;

        List<TimetableJob> allJobs = jobRepository.findAll();
        allJobs.sort(Comparator.comparing(TimetableJob::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));

        TimetableJob latestJob = allJobs.stream()
                .filter(j -> {
                    if (targetDept != null && !targetDept.isBlank() && !targetDept.equalsIgnoreCase("ALL")) {
                        if (j.getDepartment() == null || !com.easwari.backend.util.DepartmentMatcher.matches(j.getDepartment(), targetDept)) {
                            return false;
                        }
                    }
                    if (targetSem != null) {
                        if (j.getSemester() == null || !j.getSemester().equals(targetSem)) {
                            return false;
                        }
                    }
                    return true;
                })
                .findFirst()
                .orElse(null);

        if (latestJob == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> res = new HashMap<>();
        res.put("jobId", latestJob.getId());
        res.put("status", latestJob.getStatus());
        res.put("message", latestJob.getMessage() != null ? latestJob.getMessage() : "");
        res.put("department", latestJob.getDepartment() != null ? latestJob.getDepartment() : "");
        res.put("semester", latestJob.getSemester());
        res.put("totalEntries", latestJob.getTotalEntries());
        res.put("createdAt", latestJob.getCreatedAt() != null ? latestJob.getCreatedAt().toString() : "");
        res.put("completedAt", latestJob.getCompletedAt() != null ? latestJob.getCompletedAt().toString() : "");
        return ResponseEntity.ok(res);
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
