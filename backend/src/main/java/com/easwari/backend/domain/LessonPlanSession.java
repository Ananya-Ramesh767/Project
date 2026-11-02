package com.easwari.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "lesson_plan_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonPlanSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lesson_plan_id", nullable = false)
    private LessonPlan lessonPlan;

    @Column(nullable = false)
    private LocalDate sessionDate;

    @Column(nullable = false)
    private String dayOfWeek; // denormalized from sessionDate, same vocabulary as CollegeTimeslot.dayOfWeek

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "start_timeslot_id")
    private CollegeTimeslot startTimeslot; // reused unchanged: the period-timing grid; nominal slot, see section 0

    @Column(nullable = false)
    private Integer periodsCount; // contiguous periods this occurrence spans (1 for a normal
                                   // theory period, Subject.labDuration for a lab block, etc.)

    @Column(nullable = false)
    private String periodType; // THEORY | LAB | PROJECT (matches SyllabusUnit.sessionType)

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "syllabus_unit_id", nullable = false)
    private SyllabusUnit syllabusUnit; // which unit this occurrence covers

    @Column(nullable = false)
    private Integer overallSequence; // 1-based, monotonically increasing across the WHOLE
                                      // LessonPlan in date order — gives a stable "session #N" for display

    @Column(nullable = false)
    @Builder.Default
    private String status = "PLANNED"; // PLANNED | COMPLETED | RESCHEDULED

    @Column(length = 1000)
    private String remarks; // faculty-editable free text (e.g. reschedule reason, actual topics covered)

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "rescheduled_to_session_id")
    private LessonPlanSession rescheduledTo; // self-reference; set when status=RESCHEDULED,
                                              // points at the new session that replaces this one.
                                              // Original row is kept (status=RESCHEDULED), never deleted,
                                              // so the plan's history stays auditable.

    @Builder.Default
    private boolean active = true;
}
