package com.easwari.backend.dto;

import com.easwari.backend.domain.AcademicCalendar;
import com.easwari.backend.domain.LessonPlanSession;
import com.easwari.backend.domain.SubjectOffering;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response shape for LessonPlanController#generate and #getPlan — the LessonPlan
 * header fields plus its ordered sessions embedded, per
 * LESSON_PLAN_CONTRACT.md section 6.2.
 */
@Getter
@Builder
public class LessonPlanWithSessionsDto {
    private Long id;
    private SubjectOffering subjectOffering;
    private AcademicCalendar academicCalendar;
    private LocalDateTime generatedAt;
    private String generationNotes;
    private boolean active;
    private List<LessonPlanSession> sessions;
}
