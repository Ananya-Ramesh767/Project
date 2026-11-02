package com.easwari.backend.controller;

import com.easwari.backend.domain.NepCreditRule;
import com.easwari.backend.domain.Subject;
import com.easwari.backend.repository.NepCreditRuleRepository;
import com.easwari.backend.repository.SubjectRepository;
import com.easwari.backend.service.CreditCalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/nep-rules")
@RequiredArgsConstructor
public class NepRuleController {

    private final NepCreditRuleRepository nepRuleRepository;
    private final SubjectRepository subjectRepository;
    private final CreditCalculationService creditCalculationService;

    @GetMapping
    public ResponseEntity<List<NepCreditRule>> getAllRules() {
        return ResponseEntity.ok(nepRuleRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<NepCreditRule> createRule(@RequestBody NepCreditRule rule) {
        if (rule.isActive()) {
            deactivateAllRules();
        }
        NepCreditRule saved = nepRuleRepository.save(rule);
        recalculateAllSubjects();
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<NepCreditRule> updateRule(@PathVariable Long id, @RequestBody NepCreditRule rule) {
        if (!nepRuleRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        rule.setId(id);
        if (rule.isActive()) {
            deactivateAllRulesExcept(id);
        }
        NepCreditRule saved = nepRuleRepository.save(rule);
        recalculateAllSubjects();
        return ResponseEntity.ok(saved);
    }

    private void deactivateAllRules() {
        List<NepCreditRule> rules = nepRuleRepository.findAll();
        for (NepCreditRule r : rules) {
            r.setActive(false);
        }
        nepRuleRepository.saveAll(rules);
    }

    private void deactivateAllRulesExcept(Long id) {
        List<NepCreditRule> rules = nepRuleRepository.findAll();
        for (NepCreditRule r : rules) {
            if (!r.getId().equals(id)) {
                r.setActive(false);
            }
        }
        nepRuleRepository.saveAll(rules);
    }

    private void recalculateAllSubjects() {
        List<Subject> subjects = subjectRepository.findAll();
        for (Subject s : subjects) {
            creditCalculationService.updateSubjectCalculatedHours(s);
        }
        subjectRepository.saveAll(subjects);
    }
}
