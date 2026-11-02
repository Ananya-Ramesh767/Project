package com.easwari.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "syllabus_units")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyllabusUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(nullable = false)
    private String sessionType; // THEORY | LAB | PROJECT (mirrors Subject.subjectType vocabulary)

    @Column(nullable = false)
    private Integer unitNumber; // 1-based ordering WITHIN (subject, sessionType).
                                 // Never reorder/skip — this is the sequence the
                                 // generation algorithm walks strictly in order.

    @Column(nullable = false)
    private String title;

    @ElementCollection
    @CollectionTable(name = "syllabus_unit_topics", joinColumns = @JoinColumn(name = "unit_id"))
    @OrderColumn(name = "topic_order")
    @Column(name = "topic", length = 500)
    @Builder.Default
    private List<String> topics = new ArrayList<>();

    @Column(nullable = false)
    private Integer periodsAllotted; // consumed by the generation algorithm (section 3)

    @Builder.Default
    private boolean periodsAllottedIsDerived = false;
    // true when the source document gave only a track TOTAL (not a per-unit
    // figure) and this value was computed by an even split — see section 3.2.
    // Kept as a persisted flag, not just a doc comment, so the UI/audit trail can
    // visibly flag "this period count was computed, not printed in the syllabus."

    private String derivationNote; // human-readable explanation when the flag above is true; null otherwise

    @ManyToMany
    @JoinTable(
        name = "syllabus_unit_course_outcomes",
        joinColumns = @JoinColumn(name = "unit_id"),
        inverseJoinColumns = @JoinColumn(name = "course_outcome_id")
    )
    @Builder.Default
    private java.util.Set<CourseOutcome> coveredOutcomes = new java.util.HashSet<>();
    // Optional. SOURCE_MATERIAL.md does not state an explicit unit->CO mapping for
    // either course, so seed-syllabus-data.json leaves this empty; populate later
    // via the CO/unit CRUD endpoints once a subject owner enters it.

    @Builder.Default
    private boolean active = true;
}
