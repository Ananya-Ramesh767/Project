package com.easwari.backend.repository;

import com.easwari.backend.domain.LessonPlanSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LessonPlanSessionRepository extends JpaRepository<LessonPlanSession, Long> {
    List<LessonPlanSession> findByLessonPlanIdAndActiveTrueOrderByOverallSequenceAsc(Long lessonPlanId);
    Optional<LessonPlanSession> findTopByLessonPlanIdOrderByOverallSequenceDesc(Long lessonPlanId);
}
