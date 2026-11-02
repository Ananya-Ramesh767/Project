package com.easwari.backend.service;

import com.easwari.backend.domain.NepCreditRule;
import com.easwari.backend.domain.Subject;
import com.easwari.backend.repository.NepCreditRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CreditCalculationService {

    private final NepCreditRuleRepository nepCreditRuleRepository;

    public double calculateWeeklyHours(double theoryCredits, double labCredits, double tutorialCredits) {
        NepCreditRule activeRule = nepCreditRuleRepository.findFirstByActiveTrueOrderByIdDesc()
                .orElse(NepCreditRule.builder()
                        .theoryHoursPerCredit(1.0)
                        .labHoursPerCredit(2.0)
                        .tutorialHoursPerCredit(1.0)
                        .build());

        return calculateWeeklyHoursWithRule(theoryCredits, labCredits, tutorialCredits, activeRule);
    }

    public double calculateWeeklyHoursWithRule(double theoryCredits, double labCredits, double tutorialCredits, NepCreditRule rule) {
        double theoryHours = theoryCredits * rule.getTheoryHoursPerCredit();
        double labHours = labCredits * rule.getLabHoursPerCredit();
        double tutorialHours = tutorialCredits * rule.getTutorialHoursPerCredit();

        return theoryHours + labHours + tutorialHours;
    }

    public void updateSubjectCalculatedHours(Subject subject) {
        double totalHours = calculateWeeklyHours(subject.getTheoryCredits(), subject.getLabCredits(), subject.getTutorialCredits());
        subject.setCalculatedWeeklyHours(totalHours);
    }
}
