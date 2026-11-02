package com.easwari.backend.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "nep_credit_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NepCreditRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String ruleName;

    private String academicYear;
    private String program;
    private String department;

    @Column(nullable = false)
    private double theoryHoursPerCredit;

    @Column(nullable = false)
    private double labHoursPerCredit;

    @Column(nullable = false)
    private double tutorialHoursPerCredit;

    @Builder.Default
    private boolean active = true;
}
