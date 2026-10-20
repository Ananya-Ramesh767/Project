package com.odysseus.backend.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "subjects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String subjectCode;

    @Column(nullable = false)
    private String subjectName;

    @Column(nullable = false)
    private String department;

    private String facultyDepartment;

    private Integer semester;

    @Builder.Default
    @Column(nullable = false)
    private String subjectType = "THEORY"; // THEORY, LAB, TUTORIAL, THEORY_AND_LAB

    private double theoryCredits;
    private double labCredits;
    private double tutorialCredits;

    private boolean requiresLab;
    private Integer labDuration; // Continuous periods required (e.g., 2 or 3)

    private double calculatedWeeklyHours;

    @Builder.Default
    private Integer requiredFacultyCount = 1;

    public Integer getRequiredFacultyCount() {
        return (requiredFacultyCount != null && requiredFacultyCount > 0) ? requiredFacultyCount : 1;
    }

    @Builder.Default
    private boolean active = true;

    // --- Lesson Plan module additions (Person C) ---
    // Weekly period counts from the R23 curriculum's "Periods/Week (L,T,P,R)" column.
    // Distinct from theoryCredits/labCredits/tutorialCredits above, which are NEP
    // credit values consumed by CreditCalculationService. These are raw period counts
    // as printed in the curriculum document and are NOT run through any credit rule.
    private Integer weeklyLecturePeriods;   // L
    private Integer weeklyTutorialPeriods;  // T
    private Integer weeklyPracticalPeriods; // P (lab)
    private Integer weeklyProjectPeriods;   // R (project/research — distinct from P;
                                             // e.g. OOP-Java has P=0, R=4)

    // The curriculum document's own "C" (total credits) column, kept purely for
    // display/traceability back to the curriculum table. NOT used by
    // CreditCalculationService and NOT guaranteed to equal
    // theoryCredits+labCredits+tutorialCredits (those are computed via NepCreditRule;
    // this is the number printed in the syllabus document).
    private Double curriculumCredits;
}
