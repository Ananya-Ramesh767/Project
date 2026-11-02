package com.easwari.backend.controller;

import com.easwari.backend.domain.CourseObjective;
import com.easwari.backend.domain.CourseOutcome;
import com.easwari.backend.domain.Subject;
import com.easwari.backend.domain.SyllabusUnit;
import com.easwari.backend.repository.CourseObjectiveRepository;
import com.easwari.backend.repository.CourseOutcomeRepository;
import com.easwari.backend.repository.SubjectRepository;
import com.easwari.backend.repository.SyllabusUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Implements LESSON_PLAN_CONTRACT.md section 6.1 — CRUD for a subject's
 * SyllabusUnit / CourseOutcome / CourseObjective rows.
 */
@RestController
@RequestMapping("/api/lesson-plan/subjects/{subjectId}")
@RequiredArgsConstructor
public class SyllabusController {

    private final SubjectRepository subjectRepository;
    private final SyllabusUnitRepository syllabusUnitRepository;
    private final CourseOutcomeRepository courseOutcomeRepository;
    private final CourseObjectiveRepository courseObjectiveRepository;
    private final com.easwari.backend.service.PdfExtractionService pdfExtractionService;


    // ---------------------------------------------------------------- units

    @GetMapping("/units")
    public ResponseEntity<?> getUnits(@PathVariable Long subjectId,
                                       @RequestParam(required = false) String sessionType) {
        if (!subjectRepository.existsById(subjectId)) {
            return ResponseEntity.notFound().build();
        }
        List<SyllabusUnit> units = (sessionType != null)
                ? syllabusUnitRepository.findBySubjectIdAndSessionTypeAndActiveTrueOrderByUnitNumberAsc(subjectId, sessionType)
                : syllabusUnitRepository.findBySubjectIdAndActiveTrueOrderByUnitNumberAsc(subjectId);
        return ResponseEntity.ok(units);
    }

    @PostMapping("/units")
    public ResponseEntity<?> createUnit(@PathVariable Long subjectId, @RequestBody SyllabusUnit unit) {
        Subject subject = subjectRepository.findById(subjectId).orElse(null);
        if (subject == null) {
            return ResponseEntity.notFound().build();
        }
        if (syllabusUnitRepository.existsBySubjectIdAndSessionTypeAndUnitNumberAndActiveTrue(
                subjectId, unit.getSessionType(), unit.getUnitNumber())) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "An active SyllabusUnit already exists for (subject, sessionType=" + unit.getSessionType()
                            + ", unitNumber=" + unit.getUnitNumber() + ")."));
        }
        unit.setId(null);
        unit.setSubject(subject);
        unit.setActive(true);
        SyllabusUnit saved = syllabusUnitRepository.save(unit);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/units/{unitId}")
    public ResponseEntity<?> updateUnit(@PathVariable Long subjectId, @PathVariable Long unitId, @RequestBody SyllabusUnit unit) {
        SyllabusUnit existing = syllabusUnitRepository.findById(unitId).orElse(null);
        if (existing == null || !existing.getSubject().getId().equals(subjectId)) {
            return ResponseEntity.notFound().build();
        }
        if (syllabusUnitRepository.existsBySubjectIdAndSessionTypeAndUnitNumberAndActiveTrueAndIdNot(
                subjectId, unit.getSessionType(), unit.getUnitNumber(), unitId)) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Another active SyllabusUnit already occupies (sessionType=" + unit.getSessionType()
                            + ", unitNumber=" + unit.getUnitNumber() + ") for this subject."));
        }
        unit.setId(unitId);
        unit.setSubject(existing.getSubject());
        SyllabusUnit saved = syllabusUnitRepository.save(unit);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/units/{unitId}")
    public ResponseEntity<?> deleteUnit(@PathVariable Long subjectId, @PathVariable Long unitId) {
        SyllabusUnit existing = syllabusUnitRepository.findById(unitId).orElse(null);
        if (existing == null || !existing.getSubject().getId().equals(subjectId)) {
            return ResponseEntity.notFound().build();
        }
        existing.setActive(false);
        syllabusUnitRepository.save(existing);
        return ResponseEntity.ok(Map.of("message", "Syllabus unit deactivated successfully"));
    }

    /**
     * Analyses entire syllabus document across all pages and returns structured preview with metrics before confirmation.
     */
    @PostMapping(value = "/preview-syllabus-document", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> previewSyllabusDocument(
            @PathVariable Long subjectId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        Subject subject = subjectRepository.findById(subjectId).orElse(null);
        if (subject == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            Map<String, Object> preview = pdfExtractionService.analyzeSyllabusDocument(file, subject);
            return ResponseEntity.ok(preview);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to analyze syllabus document: " + e.getMessage()));
        }
    }

    /**
     * Confirms and persists the reviewed syllabus units and topics into the database.
     */
    @PostMapping("/save-syllabus-units")
    public ResponseEntity<?> saveSyllabusUnits(
            @PathVariable Long subjectId,
            @RequestBody Object payload) {
        Subject subject = subjectRepository.findById(subjectId).orElse(null);
        if (subject == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            List<Map<String, Object>> unitsList = new java.util.ArrayList<>();
            List<Map<String, Object>> objectives = null;
            List<Map<String, Object>> outcomes = null;

            if (payload instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> l = (List<Map<String, Object>>) payload;
                unitsList = l;
            } else if (payload instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) payload;
                if (map.containsKey("units") && map.get("units") instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> ul = (List<Map<String, Object>>) map.get("units");
                    unitsList = ul;
                }
                if (map.containsKey("courseObjectives") && map.get("courseObjectives") instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> ol = (List<Map<String, Object>>) map.get("courseObjectives");
                    objectives = ol;
                }
                if (map.containsKey("courseOutcomes") && map.get("courseOutcomes") instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> col = (List<Map<String, Object>>) map.get("courseOutcomes");
                    outcomes = col;
                }
            }

            List<SyllabusUnit> savedUnits = new java.util.ArrayList<>();
            for (Map<String, Object> map : unitsList) {
                String sessionType = (String) map.getOrDefault("sessionType", "THEORY");
                Integer unitNumber = (map.get("unitNumber") instanceof Number)
                        ? ((Number) map.get("unitNumber")).intValue() : 1;
                String title = (String) map.getOrDefault("title", "Unit " + unitNumber);
                Integer periods = (map.get("periodsAllotted") instanceof Number)
                        ? ((Number) map.get("periodsAllotted")).intValue() : 9;
                @SuppressWarnings("unchecked")
                List<String> topics = (List<String>) map.getOrDefault("topics", java.util.Collections.emptyList());

                java.util.Optional<SyllabusUnit> existingOpt = syllabusUnitRepository
                        .findBySubjectIdAndSessionTypeAndUnitNumberAndActiveTrue(subjectId, sessionType, unitNumber);

                SyllabusUnit u = existingOpt.orElseGet(SyllabusUnit::new);
                u.setSubject(subject);
                u.setSessionType(sessionType);
                u.setUnitNumber(unitNumber);
                u.setTitle(title);
                u.setTopics(topics);
                u.setPeriodsAllotted(periods);
                u.setPeriodsAllottedIsDerived(false);
                u.setActive(true);
                savedUnits.add(syllabusUnitRepository.save(u));
            }

            int savedObjCount = 0;
            if (objectives != null) {
                for (Map<String, Object> oMap : objectives) {
                    Integer seq = (oMap.get("sequenceNumber") instanceof Number)
                            ? ((Number) oMap.get("sequenceNumber")).intValue() : 1;
                    String desc = (String) oMap.getOrDefault("description", "");
                    if (desc != null && !desc.trim().isEmpty()) {
                        java.util.Optional<CourseObjective> existOpt = courseObjectiveRepository
                                .findBySubjectIdAndActiveTrueOrderBySequenceNumberAsc(subjectId).stream()
                                .filter(o -> java.util.Objects.equals(o.getSequenceNumber(), seq))
                                .findFirst();
                        CourseObjective obj = existOpt.orElseGet(CourseObjective::new);
                        obj.setSubject(subject);
                        obj.setSequenceNumber(seq);
                        obj.setDescription(desc.trim());
                        obj.setActive(true);
                        courseObjectiveRepository.save(obj);
                        savedObjCount++;
                    }
                }
            }

            int savedCoCount = 0;
            if (outcomes != null) {
                for (Map<String, Object> coMap : outcomes) {
                    Integer coNum = (coMap.get("coNumber") instanceof Number)
                            ? ((Number) coMap.get("coNumber")).intValue() : 1;
                    String desc = (String) coMap.getOrDefault("description", "");
                    if (desc != null && !desc.trim().isEmpty()) {
                        java.util.Optional<CourseOutcome> existOpt = courseOutcomeRepository
                                .findBySubjectIdAndActiveTrueOrderByCoNumberAsc(subjectId).stream()
                                .filter(c -> java.util.Objects.equals(c.getCoNumber(), coNum))
                                .findFirst();
                        CourseOutcome co = existOpt.orElseGet(CourseOutcome::new);
                        co.setSubject(subject);
                        co.setCoNumber(coNum);
                        co.setDescription(desc.trim());
                        co.setActive(true);
                        courseOutcomeRepository.save(co);
                        savedCoCount++;
                    }
                }
            }

            return ResponseEntity.ok(Map.of(
                    "savedCount", savedUnits.size(),
                    "units", savedUnits,
                    "savedObjectivesCount", savedObjCount,
                    "savedOutcomesCount", savedCoCount,
                    "message", "Successfully confirmed and saved " + savedUnits.size() + " units, "
                            + savedObjCount + " course objectives, and " + savedCoCount + " course outcomes."
            ));
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to save syllabus data: " + e.getMessage()));
        }
    }

    /**
     * Extracts and automatically saves Syllabus Units & Topics, Objectives, and Outcomes from an uploaded Syllabus PDF.
     */
    @PostMapping(value = "/upload-syllabus-document", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadSyllabusDocument(
            @PathVariable Long subjectId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        Subject subject = subjectRepository.findById(subjectId).orElse(null);
        if (subject == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            String text = pdfExtractionService.extractTextFromFile(file);
            List<Map<String, Object>> parsed = pdfExtractionService.parseSyllabusUnits(text);
            List<Map<String, Object>> parsedObj = pdfExtractionService.parseCourseObjectives(text);
            List<Map<String, Object>> parsedOutcomes = pdfExtractionService.parseCourseOutcomes(text);

            if (parsed.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "No syllabus units could be detected in this document."));
            }

            List<SyllabusUnit> savedUnits = new java.util.ArrayList<>();
            for (Map<String, Object> map : parsed) {
                String sessionType = (String) map.getOrDefault("sessionType", "THEORY");
                Integer unitNumber = (Integer) map.getOrDefault("unitNumber", 1);
                String title = (String) map.getOrDefault("title", "Unit " + unitNumber);
                Integer periods = (Integer) map.getOrDefault("periodsAllotted", 9);
                @SuppressWarnings("unchecked")
                List<String> topics = (List<String>) map.getOrDefault("topics", java.util.Collections.emptyList());

                java.util.Optional<SyllabusUnit> existingOpt = syllabusUnitRepository
                        .findBySubjectIdAndSessionTypeAndUnitNumberAndActiveTrue(subjectId, sessionType, unitNumber);

                SyllabusUnit u = existingOpt.orElseGet(SyllabusUnit::new);
                u.setSubject(subject);
                u.setSessionType(sessionType);
                u.setUnitNumber(unitNumber);
                u.setTitle(title);
                u.setTopics(topics);
                u.setPeriodsAllotted(periods);
                u.setPeriodsAllottedIsDerived(false);
                u.setActive(true);
                savedUnits.add(syllabusUnitRepository.save(u));
            }

            int savedObjCount = 0;
            for (Map<String, Object> oMap : parsedObj) {
                Integer seq = (Integer) oMap.getOrDefault("sequenceNumber", 1);
                String desc = (String) oMap.getOrDefault("description", "");
                if (desc != null && !desc.trim().isEmpty()) {
                    CourseObjective obj = courseObjectiveRepository
                            .findBySubjectIdAndActiveTrueOrderBySequenceNumberAsc(subjectId).stream()
                            .filter(o -> java.util.Objects.equals(o.getSequenceNumber(), seq))
                            .findFirst().orElseGet(CourseObjective::new);
                    obj.setSubject(subject);
                    obj.setSequenceNumber(seq);
                    obj.setDescription(desc.trim());
                    obj.setActive(true);
                    courseObjectiveRepository.save(obj);
                    savedObjCount++;
                }
            }

            int savedCoCount = 0;
            for (Map<String, Object> coMap : parsedOutcomes) {
                Integer coNum = (Integer) coMap.getOrDefault("coNumber", 1);
                String desc = (String) coMap.getOrDefault("description", "");
                if (desc != null && !desc.trim().isEmpty()) {
                    CourseOutcome co = courseOutcomeRepository
                            .findBySubjectIdAndActiveTrueOrderByCoNumberAsc(subjectId).stream()
                            .filter(c -> java.util.Objects.equals(c.getCoNumber(), coNum))
                            .findFirst().orElseGet(CourseOutcome::new);
                    co.setSubject(subject);
                    co.setCoNumber(coNum);
                    co.setDescription(desc.trim());
                    co.setActive(true);
                    courseOutcomeRepository.save(co);
                    savedCoCount++;
                }
            }

            return ResponseEntity.ok(Map.of(
                    "totalUnits", savedUnits.size(),
                    "units", savedUnits,
                    "savedObjectivesCount", savedObjCount,
                    "savedOutcomesCount", savedCoCount,
                    "message", "Successfully parsed and saved " + savedUnits.size() + " units, "
                            + savedObjCount + " objectives, and " + savedCoCount + " outcomes!"
            ));
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to parse syllabus document: " + e.getMessage()));
        }
    }


    // ------------------------------------------------------------- outcomes

    @GetMapping("/outcomes")
    public ResponseEntity<?> getOutcomes(@PathVariable Long subjectId) {
        if (!subjectRepository.existsById(subjectId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(courseOutcomeRepository.findBySubjectIdAndActiveTrueOrderByCoNumberAsc(subjectId));
    }

    @PostMapping("/outcomes")
    public ResponseEntity<?> createOutcome(@PathVariable Long subjectId, @RequestBody CourseOutcome outcome) {
        Subject subject = subjectRepository.findById(subjectId).orElse(null);
        if (subject == null) {
            return ResponseEntity.notFound().build();
        }
        if (courseOutcomeRepository.existsBySubjectIdAndCoNumberAndActiveTrue(subjectId, outcome.getCoNumber())) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "An active CourseOutcome with coNumber=" + outcome.getCoNumber() + " already exists for this subject."));
        }
        outcome.setId(null);
        outcome.setSubject(subject);
        outcome.setActive(true);
        CourseOutcome saved = courseOutcomeRepository.save(outcome);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/outcomes/{id}")
    public ResponseEntity<?> updateOutcome(@PathVariable Long subjectId, @PathVariable Long id, @RequestBody CourseOutcome outcome) {
        CourseOutcome existing = courseOutcomeRepository.findById(id).orElse(null);
        if (existing == null || !existing.getSubject().getId().equals(subjectId)) {
            return ResponseEntity.notFound().build();
        }
        if (courseOutcomeRepository.existsBySubjectIdAndCoNumberAndActiveTrueAndIdNot(subjectId, outcome.getCoNumber(), id)) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Another active CourseOutcome already uses coNumber=" + outcome.getCoNumber() + " for this subject."));
        }
        outcome.setId(id);
        outcome.setSubject(existing.getSubject());
        CourseOutcome saved = courseOutcomeRepository.save(outcome);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/outcomes/{id}")
    public ResponseEntity<?> deleteOutcome(@PathVariable Long subjectId, @PathVariable Long id) {
        CourseOutcome existing = courseOutcomeRepository.findById(id).orElse(null);
        if (existing == null || !existing.getSubject().getId().equals(subjectId)) {
            return ResponseEntity.notFound().build();
        }
        existing.setActive(false);
        courseOutcomeRepository.save(existing);
        return ResponseEntity.ok(Map.of("message", "Course outcome deactivated successfully"));
    }

    // ----------------------------------------------------------- objectives

    @GetMapping("/objectives")
    public ResponseEntity<?> getObjectives(@PathVariable Long subjectId) {
        if (!subjectRepository.existsById(subjectId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(courseObjectiveRepository.findBySubjectIdAndActiveTrueOrderBySequenceNumberAsc(subjectId));
    }

    @PostMapping("/objectives")
    public ResponseEntity<?> createObjective(@PathVariable Long subjectId, @RequestBody CourseObjective objective) {
        Subject subject = subjectRepository.findById(subjectId).orElse(null);
        if (subject == null) {
            return ResponseEntity.notFound().build();
        }
        if (courseObjectiveRepository.existsBySubjectIdAndSequenceNumberAndActiveTrue(subjectId, objective.getSequenceNumber())) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "An active CourseObjective with sequenceNumber=" + objective.getSequenceNumber() + " already exists for this subject."));
        }
        objective.setId(null);
        objective.setSubject(subject);
        objective.setActive(true);
        CourseObjective saved = courseObjectiveRepository.save(objective);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/objectives/{id}")
    public ResponseEntity<?> updateObjective(@PathVariable Long subjectId, @PathVariable Long id, @RequestBody CourseObjective objective) {
        CourseObjective existing = courseObjectiveRepository.findById(id).orElse(null);
        if (existing == null || !existing.getSubject().getId().equals(subjectId)) {
            return ResponseEntity.notFound().build();
        }
        if (courseObjectiveRepository.existsBySubjectIdAndSequenceNumberAndActiveTrueAndIdNot(subjectId, objective.getSequenceNumber(), id)) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Another active CourseObjective already uses sequenceNumber=" + objective.getSequenceNumber() + " for this subject."));
        }
        objective.setId(id);
        objective.setSubject(existing.getSubject());
        CourseObjective saved = courseObjectiveRepository.save(objective);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/objectives/{id}")
    public ResponseEntity<?> deleteObjective(@PathVariable Long subjectId, @PathVariable Long id) {
        CourseObjective existing = courseObjectiveRepository.findById(id).orElse(null);
        if (existing == null || !existing.getSubject().getId().equals(subjectId)) {
            return ResponseEntity.notFound().build();
        }
        existing.setActive(false);
        courseObjectiveRepository.save(existing);
        return ResponseEntity.ok(Map.of("message", "Course objective deactivated successfully"));
    }
}
