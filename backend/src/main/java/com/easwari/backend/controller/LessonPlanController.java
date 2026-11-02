package com.easwari.backend.controller;

import com.easwari.backend.domain.*;
import com.easwari.backend.dto.LessonPlanWithSessionsDto;
import com.easwari.backend.dto.SessionUpdateRequestDto;
import com.easwari.backend.dto.SessionUpdateResponseDto;
import com.easwari.backend.repository.*;
import com.easwari.backend.service.LessonPlanGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 * Manages lesson plan generation, fetching, and session tracking/rescheduling.
 */
@RestController
@RequestMapping("/api/lesson-plan")
@RequiredArgsConstructor
public class LessonPlanController {

    private final SubjectOfferingRepository subjectOfferingRepository;
    private final AcademicCalendarRepository academicCalendarRepository;
    private final LessonPlanRepository lessonPlanRepository;
    private final LessonPlanSessionRepository lessonPlanSessionRepository;
    private final LessonPlanGenerationService generationService;
    private final UserRepository userRepository;
    private final FacultyRepository facultyRepository;

    private static String normalizeEmail(String email) {
        if (email == null) return "";
        return email.trim().toLowerCase().replaceAll("\\.+", ".").replaceAll("\\.@|@\\.", "@");
    }

    private static String cleanName(String name) {
        if (name == null) return "";
        return name.toLowerCase()
                .replaceAll("\\b(dr|mr|mrs|ms|prof|asst|assoc)\\b", "")
                .replaceAll("[^a-z0-9]", "");
    }

    private boolean isUserAuthorizedForOffering(SubjectOffering offering) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) return true;

        String username = auth.getName();
        User user = userRepository.findByUsername(username)
                .or(() -> userRepository.findByEmail(username))
                .orElse(null);
        if (user == null) return false;

        Faculty assigned = offering.getAssignedFaculty();
        if (assigned == null) return false;

        String uEmail = user.getEmail() != null ? user.getEmail() : "";
        String fEmail = assigned.getEmail() != null ? assigned.getEmail() : "";

        // 1. Raw match
        if (!uEmail.isBlank() && !fEmail.isBlank() && uEmail.equalsIgnoreCase(fEmail)) return true;

        // 2. Normalized email match
        if (!uEmail.isBlank() && !fEmail.isBlank() && normalizeEmail(uEmail).equals(normalizeEmail(fEmail))) return true;

        // 3. Name match
        String uClean = cleanName(user.getFullName() != null ? user.getFullName() : user.getUsername());
        String fClean = cleanName(assigned.getName());
        if (!uClean.isBlank() && !fClean.isBlank() && (uClean.equals(fClean) || uClean.contains(fClean) || fClean.contains(uClean))) return true;

        // 4. Employee ID match
        if (assigned.getEmployeeId() != null && assigned.getEmployeeId().equalsIgnoreCase(user.getUsername())) return true;

        return false;
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestParam Long subjectOfferingId,
                                       @RequestParam Long academicCalendarId,
                                       @RequestParam(defaultValue = "false") boolean force) {
        SubjectOffering offering = subjectOfferingRepository.findById(subjectOfferingId).orElse(null);
        if (offering == null) {
            return ResponseEntity.notFound().build();
        }
        if (!isUserAuthorizedForOffering(offering)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied: You are not assigned to this subject offering."));
        }
        AcademicCalendar calendar = academicCalendarRepository.findById(academicCalendarId).orElse(null);
        if (calendar == null) {
            return ResponseEntity.notFound().build();
        }

        Optional<LessonPlan> existing = lessonPlanRepository
                .findBySubjectOfferingIdAndAcademicCalendarIdAndActiveTrue(subjectOfferingId, academicCalendarId);

        if (existing.isPresent() && !force) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error",
                    "An active lesson plan already exists for this offering/calendar. Pass force=true to regenerate."));
        }

        if (existing.isPresent()) {
            LessonPlan old = existing.get();
            List<LessonPlanSession> oldSessions = lessonPlanSessionRepository
                    .findByLessonPlanIdAndActiveTrueOrderByOverallSequenceAsc(old.getId());
            for (LessonPlanSession s : oldSessions) {
                s.setActive(false);
            }
            lessonPlanSessionRepository.saveAll(oldSessions);
            old.setActive(false);
            lessonPlanRepository.save(old);
        }

        try {
            LessonPlan plan = generationService.generate(offering, calendar);
            List<LessonPlanSession> sessions = lessonPlanSessionRepository
                    .findByLessonPlanIdAndActiveTrueOrderByOverallSequenceAsc(plan.getId());
            return ResponseEntity.ok(toDto(plan, sessions));
        } catch (LessonPlanGenerationService.GenerationException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/{subjectOfferingId}")
    public ResponseEntity<?> getPlan(@PathVariable Long subjectOfferingId,
                                      @RequestParam(required = false) Long academicCalendarId) {
        Optional<LessonPlan> planOpt = (academicCalendarId != null)
                ? lessonPlanRepository.findBySubjectOfferingIdAndAcademicCalendarIdAndActiveTrue(subjectOfferingId, academicCalendarId)
                : lessonPlanRepository.findFirstBySubjectOfferingIdAndActiveTrueOrderByGeneratedAtDesc(subjectOfferingId);

        if (planOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        LessonPlan plan = planOpt.get();
        List<LessonPlanSession> sessions = lessonPlanSessionRepository
                .findByLessonPlanIdAndActiveTrueOrderByOverallSequenceAsc(plan.getId());
        return ResponseEntity.ok(toDto(plan, sessions));
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<?> getSession(@PathVariable Long sessionId) {
        return lessonPlanSessionRepository.findById(sessionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/session/{sessionId}")
    public ResponseEntity<?> updateSession(@PathVariable Long sessionId, @RequestBody SessionUpdateRequestDto request) {
        LessonPlanSession original = lessonPlanSessionRepository.findById(sessionId).orElse(null);
        if (original == null) {
            return ResponseEntity.notFound().build();
        }

        if ("COMPLETED".equals(request.getStatus())) {
            original.setStatus("COMPLETED");
            if (request.getRemarks() != null) {
                original.setRemarks(request.getRemarks());
            }
            lessonPlanSessionRepository.save(original);
            return ResponseEntity.ok(SessionUpdateResponseDto.builder().original(original).replacement(null).build());
        }

        if ("RESCHEDULED".equals(request.getStatus())) {
            LocalDate rescheduledDate = request.getRescheduledDate();
            if (rescheduledDate == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "rescheduledDate is required when status=RESCHEDULED."));
            }

            int nextSequence = lessonPlanSessionRepository
                    .findTopByLessonPlanIdOrderByOverallSequenceDesc(original.getLessonPlan().getId())
                    .map(s -> s.getOverallSequence() + 1)
                    .orElse(1);

            LessonPlanSession replacement = LessonPlanSession.builder()
                    .lessonPlan(original.getLessonPlan())
                    .sessionDate(rescheduledDate)
                    .dayOfWeek(rescheduledDate.getDayOfWeek().name())
                    .startTimeslot(original.getStartTimeslot())
                    .periodsCount(original.getPeriodsCount())
                    .periodType(original.getPeriodType())
                    .syllabusUnit(original.getSyllabusUnit())
                    .overallSequence(nextSequence)
                    .status("PLANNED")
                    .active(true)
                    .build();
            replacement = lessonPlanSessionRepository.save(replacement);

            original.setStatus("RESCHEDULED");
            if (request.getRemarks() != null) {
                original.setRemarks(request.getRemarks());
            }
            original.setRescheduledTo(replacement);
            lessonPlanSessionRepository.save(original);

            return ResponseEntity.ok(SessionUpdateResponseDto.builder().original(original).replacement(replacement).build());
        }

        return ResponseEntity.badRequest().body(Map.of("error", "status must be COMPLETED or RESCHEDULED."));
    }

    @GetMapping("/faculty/{facultyId}")
    public ResponseEntity<List<LessonPlan>> getPlansForFaculty(@PathVariable Long facultyId) {
        return ResponseEntity.ok(lessonPlanRepository.findBySubjectOffering_AssignedFaculty_IdAndActiveTrue(facultyId));
    }

    @GetMapping("/my-plans")
    public ResponseEntity<?> getMyPlans() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated."));
        }
        User user = userRepository.findByUsername(authentication.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated."));
        }
        Faculty faculty = facultyRepository.findByActiveTrue().stream()
                .filter(f -> f.getEmail() != null && f.getEmail().equalsIgnoreCase(user.getEmail()))
                .findFirst()
                .orElse(null);
        if (faculty == null) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "No Faculty record is linked to this account's email (" + user.getEmail() + ")."));
        }
        return ResponseEntity.ok(lessonPlanRepository.findBySubjectOffering_AssignedFaculty_IdAndActiveTrue(faculty.getId()));
    }

    @GetMapping("/admin/overview")
    public ResponseEntity<List<Map<String, Object>>> getAdminOverview() {
        List<LessonPlan> plans = lessonPlanRepository.findByActiveTrue();
        List<Map<String, Object>> overviewList = new ArrayList<>();

        for (LessonPlan plan : plans) {
            List<LessonPlanSession> sessions = lessonPlanSessionRepository
                    .findByLessonPlanIdAndActiveTrueOrderByOverallSequenceAsc(plan.getId());

            int total = sessions.size();
            long completed = sessions.stream().filter(s -> "COMPLETED".equalsIgnoreCase(s.getStatus())).count();
            long rescheduled = sessions.stream().filter(s -> "RESCHEDULED".equalsIgnoreCase(s.getStatus())).count();
            double progress = total > 0 ? ((double) completed / total) * 100.0 : 0.0;

            Map<String, Object> item = new HashMap<>();
            item.put("lessonPlanId", plan.getId());
            item.put("batchName", plan.getSubjectOffering() != null && plan.getSubjectOffering().getBatch() != null 
                    ? plan.getSubjectOffering().getBatch().getBatchName() : "N/A");
            item.put("subjectCode", plan.getSubjectOffering() != null && plan.getSubjectOffering().getSubject() != null 
                    ? plan.getSubjectOffering().getSubject().getSubjectCode() : "N/A");
            item.put("subjectName", plan.getSubjectOffering() != null && plan.getSubjectOffering().getSubject() != null 
                    ? plan.getSubjectOffering().getSubject().getSubjectName() : "N/A");
            item.put("facultyName", plan.getSubjectOffering() != null && plan.getSubjectOffering().getAssignedFaculty() != null 
                    ? plan.getSubjectOffering().getAssignedFaculty().getName() : "Unassigned");
            item.put("totalSessions", total);
            item.put("completedSessions", completed);
            item.put("rescheduledSessions", rescheduled);
            item.put("completionPercentage", Math.round(progress));

            overviewList.add(item);
        }

        return ResponseEntity.ok(overviewList);
    }

    private LessonPlanWithSessionsDto toDto(LessonPlan plan, List<LessonPlanSession> sessions) {
        return LessonPlanWithSessionsDto.builder()
                .id(plan.getId())
                .subjectOffering(plan.getSubjectOffering())
                .academicCalendar(plan.getAcademicCalendar())
                .generatedAt(plan.getGeneratedAt())
                .generationNotes(plan.getGenerationNotes())
                .active(plan.isActive())
                .sessions(sessions)
                .build();
    }
}
