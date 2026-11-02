package com.easwari.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "lesson_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "subject_offering_id", nullable = false)
    private SubjectOffering subjectOffering; // reused unchanged: links batch+subject+faculty

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "academic_calendar_id", nullable = false)
    private AcademicCalendar academicCalendar; // reused unchanged: working-day source of truth

    private LocalDateTime generatedAt;

    @Column(length = 2000)
    private String generationNotes; // e.g. "THEORY: Mon/Wed; PROJECT: Tue/Thu (2p);
                                     // Unit Test I (22-24 Jul) not auto-excluded, see contract 3.3"

    @Builder.Default
    private boolean active = true;
}
