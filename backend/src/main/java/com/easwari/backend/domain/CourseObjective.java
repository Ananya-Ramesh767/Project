package com.easwari.backend.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "course_objectives")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseObjective {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(nullable = false)
    private Integer sequenceNumber; // 1-based; the curriculum doc's objectives are
                                     // unnumbered ("To ..." bullets) — sequence is
                                     // for stable ordering/display only, not a CO-style code

    @Column(nullable = false, length = 1000)
    private String description;

    @Builder.Default
    private boolean active = true;
}
