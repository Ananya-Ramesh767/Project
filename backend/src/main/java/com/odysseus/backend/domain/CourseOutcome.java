package com.odysseus.backend.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "course_outcomes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseOutcome {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(nullable = false)
    private Integer coNumber; // 1..N, matches the curriculum doc's "CO1..CO5" numbering exactly

    @Column(nullable = false, length = 4000)
    private String description;

    @Builder.Default
    private boolean active = true;
}
