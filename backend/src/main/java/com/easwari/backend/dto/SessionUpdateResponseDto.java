package com.easwari.backend.dto;

import com.easwari.backend.domain.LessonPlanSession;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SessionUpdateResponseDto {
    private LessonPlanSession original;
    private LessonPlanSession replacement; // null unless status = RESCHEDULED
}
