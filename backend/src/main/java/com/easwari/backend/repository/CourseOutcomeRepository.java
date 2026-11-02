package com.easwari.backend.repository;

import com.easwari.backend.domain.CourseOutcome;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseOutcomeRepository extends JpaRepository<CourseOutcome, Long> {
    List<CourseOutcome> findBySubjectIdAndActiveTrueOrderByCoNumberAsc(Long subjectId);
    boolean existsBySubjectIdAndCoNumberAndActiveTrue(Long subjectId, Integer coNumber);
    boolean existsBySubjectIdAndCoNumberAndActiveTrueAndIdNot(Long subjectId, Integer coNumber, Long id);
}
