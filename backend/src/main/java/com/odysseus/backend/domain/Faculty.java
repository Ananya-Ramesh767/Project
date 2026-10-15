package com.odysseus.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "faculty")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Faculty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String employeeId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String department;

    private String designation;
    private String specialization;
    private String subjectsHandled;

    private Integer maxWeeklyLoad;

    @Builder.Default
    private boolean active = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "faculty_subject_eligibility",
        joinColumns = @JoinColumn(name = "faculty_id"),
        inverseJoinColumns = @JoinColumn(name = "subject_id")
    )
    @Builder.Default
    private Set<Subject> eligibleSubjects = new HashSet<>();
}
