package com.odysseus.backend.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity representing a single scheduled assignment (lecture, lab, or special activity).
 * Composite uniqueness is enforced on batch, subject, academic year, semester, day, period,
 * lab flag and activity type to prevent accidental duplicate rows while still allowing
 * legitimate multiple periods (e.g., multi‑period labs or special activities).
 */
@Entity
@Table(name = "timetable_assignments",
       uniqueConstraints = @UniqueConstraint(columnNames = {
               "batch_id", "subject_id", "academic_year", "semester",
               "day_of_week", "period_number", "is_lab_block", "activity_type"
       }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimetableAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @ManyToOne
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @ManyToOne
    @JoinColumn(name = "faculty_id", nullable = true)
    private Faculty faculty;

    @ManyToOne(optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false)
    private String dayOfWeek; // MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY

    @Column(nullable = false)
    private Integer periodNumber; // 1‑8

    @Builder.Default
    private Integer durationPeriods = 1; // 1 for theory/tutorial, 2‑3 for continuous lab blocks

    @Builder.Default
    private boolean isLabBlock = false;

    // Activity types: ACADEMIC, LAB, APTITUDE, TECHNICAL_TEST, NPTEL, COUNSELLING,
    // LEETCODE, CAREER_SKILL_DEVELOPMENT, FREE, etc.
    @Builder.Default
    private String activityType = "ACADEMIC";

    private String academicYear;

    private Integer semester;

    @Builder.Default
    private boolean active = true;
}
