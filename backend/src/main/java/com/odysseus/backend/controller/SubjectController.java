package com.odysseus.backend.controller;

import com.odysseus.backend.domain.Subject;
import com.odysseus.backend.repository.SubjectRepository;
import com.odysseus.backend.service.CreditCalculationService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/subjects")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectRepository subjectRepository;
    private final CreditCalculationService creditCalculationService;
    private final com.odysseus.backend.service.PdfExtractionService pdfExtractionService;
    private final com.odysseus.backend.repository.SubjectOfferingRepository offeringRepository;
    private final com.odysseus.backend.repository.TimetableAssignmentRepository assignmentRepository;
    private final com.odysseus.backend.repository.TimetableEntryRepository entryRepository;
    private final com.odysseus.backend.repository.FacultyRepository facultyRepository;
    private final com.odysseus.backend.repository.SyllabusUnitRepository syllabusUnitRepository;

    @GetMapping
    public ResponseEntity<List<Subject>> getAllSubjects(
            @RequestParam(required = false) Integer semester,
            @RequestParam(required = false) String department) {
        List<Subject> subjects = subjectRepository.findAll();
        if (semester != null) {
            subjects = subjects.stream().filter(s -> Objects.equals(s.getSemester(), semester)).collect(Collectors.toList());
        }
        if (department != null && !department.trim().isEmpty() && !department.equalsIgnoreCase("ALL")) {
            subjects = subjects.stream().filter(s -> com.odysseus.backend.util.DepartmentMatcher.matches(s.getDepartment(), department)).collect(Collectors.toList());
        }
        return ResponseEntity.ok(subjects);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Subject> getSubjectById(@PathVariable Long id) {
        return subjectRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createSubject(@RequestBody Subject subject) {
        if (subjectRepository.existsBySubjectCode(subject.getSubjectCode())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Subject code [" + subject.getSubjectCode() + "] already exists."));
        }
        creditCalculationService.updateSubjectCalculatedHours(subject);
        Subject saved = subjectRepository.save(subject);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateSubject(@PathVariable Long id, @RequestBody Subject subject) {
        if (!subjectRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        if (subjectRepository.existsBySubjectCodeAndIdNot(subject.getSubjectCode(), id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Subject code [" + subject.getSubjectCode() + "] is taken by another subject."));
        }
        subject.setId(id);
        creditCalculationService.updateSubjectCalculatedHours(subject);
        Subject saved = subjectRepository.save(subject);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> deleteSubject(@PathVariable Long id) {
        return subjectRepository.findById(id).map(subject -> {
            // 1. Remove from all subject offerings
            List<com.odysseus.backend.domain.SubjectOffering> offerings = offeringRepository.findAll().stream()
                    .filter(o -> o.getSubject() != null && Objects.equals(o.getSubject().getId(), id))
                    .collect(Collectors.toList());
            offeringRepository.deleteAll(offerings);

            // 2. Remove timetable assignments
            List<com.odysseus.backend.domain.TimetableAssignment> assignments = assignmentRepository.findAll().stream()
                    .filter(a -> a.getSubject() != null && Objects.equals(a.getSubject().getId(), id))
                    .collect(Collectors.toList());
            assignmentRepository.deleteAll(assignments);

            // 3. Remove timetable entries
            List<com.odysseus.backend.domain.TimetableEntry> entries = entryRepository.findAll().stream()
                    .filter(e -> Objects.equals(e.getSubjectId(), id))
                    .collect(Collectors.toList());
            entryRepository.deleteAll(entries);

            // 4. Remove syllabus units
            List<com.odysseus.backend.domain.SyllabusUnit> units = syllabusUnitRepository.findBySubjectIdAndActiveTrueOrderByUnitNumberAsc(id);
            syllabusUnitRepository.deleteAll(units);

            // 5. Unlink faculty eligible subjects
            List<com.odysseus.backend.domain.Faculty> facultyList = facultyRepository.findAll();
            for (com.odysseus.backend.domain.Faculty f : facultyList) {
                if (f.getEligibleSubjects() != null && f.getEligibleSubjects().removeIf(s -> Objects.equals(s.getId(), id))) {
                    facultyRepository.save(f);
                }
            }

            // 6. Delete subject record permanently
            subjectRepository.delete(subject);

            return ResponseEntity.ok(Map.of("message", "Subject [" + subject.getSubjectCode() + "] and related records deleted successfully."));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/calculate-hours")
    public ResponseEntity<?> calculateHours(@RequestBody Map<String, Double> payload) {
        double theory = payload.getOrDefault("theoryCredits", 0.0);
        double lab = payload.getOrDefault("labCredits", 0.0);
        double tutorial = payload.getOrDefault("tutorialCredits", 0.0);

        double totalHours = creditCalculationService.calculateWeeklyHours(theory, lab, tutorial);
        return ResponseEntity.ok(Map.of(
                "theoryHours", theory,
                "labHours", lab * 2.0,
                "tutorialHours", tutorial,
                "totalWeeklyHours", totalHours
        ));
    }

    @Data
    public static class DocumentParseRequest {
        private String rawText;
        private Integer defaultSemester;
        private String defaultDepartment;
    }

    @PostMapping(value = "/parse-document", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> parseSubjectDocument(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "department", defaultValue = "Computer Science Engineering") String department,
            @RequestParam(value = "semester", defaultValue = "5") Integer semester) {
        try {
            String text = pdfExtractionService.extractTextFromFile(file);
            boolean isScanned = (text == null || text.trim().isEmpty());
            List<Map<String, Object>> subjects = isScanned ? Collections.emptyList() : pdfExtractionService.parseCurriculumSubjects(text, semester, department);
            return ResponseEntity.ok(Map.of(
                    "totalExtracted", subjects.size(),
                    "preview", subjects,
                    "isScannedImage", isScanned,
                    "fileName", file.getOriginalFilename() != null ? file.getOriginalFilename() : ""
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Unable to extract document: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/parse-text", consumes = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> parseSubjectText(@RequestBody DocumentParseRequest request) {
        if (request.getRawText() == null || request.getRawText().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "rawText content is empty."));
        }
        List<Map<String, Object>> subjects = pdfExtractionService.parseCurriculumSubjects(
                request.getRawText(), request.getDefaultSemester(), request.getDefaultDepartment());
        return ResponseEntity.ok(Map.of(
                "totalExtracted", subjects.size(),
                "preview", subjects
        ));
    }

    @PostMapping("/batch-save")
    public ResponseEntity<?> batchSaveSubjects(@RequestBody List<Subject> subjects) {
        List<Subject> savedList = new ArrayList<>();
        List<String> updatedCodes = new ArrayList<>();

        for (Subject sub : subjects) {
            Optional<Subject> existing = subjectRepository.findBySubjectCode(sub.getSubjectCode());
            if (existing.isPresent()) {
                Subject exist = existing.get();
                exist.setSubjectName(sub.getSubjectName());
                exist.setDepartment(sub.getDepartment());
                exist.setFacultyDepartment(sub.getFacultyDepartment());
                exist.setDepartmentClassificationSource(sub.getDepartmentClassificationSource());
                exist.setDepartmentClassificationReference(sub.getDepartmentClassificationReference());
                exist.setSemester(sub.getSemester());
                exist.setSubjectType(sub.getSubjectType());
                exist.setTheoryCredits(sub.getTheoryCredits());
                exist.setLabCredits(sub.getLabCredits());
                exist.setTutorialCredits(sub.getTutorialCredits());
                exist.setRequiresLab(sub.isRequiresLab());
                exist.setLabDuration(sub.getLabDuration());
                exist.setActive(true);
                creditCalculationService.updateSubjectCalculatedHours(exist);
                savedList.add(subjectRepository.save(exist));
                updatedCodes.add(exist.getSubjectCode());
            } else {
                creditCalculationService.updateSubjectCalculatedHours(sub);
                sub.setActive(true);
                savedList.add(subjectRepository.save(sub));
            }
        }

        return ResponseEntity.ok(Map.of(
                "savedCount", savedList.size(),
                "savedSubjects", savedList,
                "updatedCodes", updatedCodes
        ));
    }
}
