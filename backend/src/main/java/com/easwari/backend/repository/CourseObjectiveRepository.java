package com.easwari.backend.repository;

import com.easwari.backend.domain.CourseObjective;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseObjectiveRepository extends JpaRepository<CourseObjective, Long> {
    List<CourseObjective> findBySubjectIdAndActiveTrueOrderBySequenceNumberAsc(Long subjectId);
    boolean existsBySubjectIdAndSequenceNumberAndActiveTrue(Long subjectId, Integer sequenceNumber);
    boolean existsBySubjectIdAndSequenceNumberAndActiveTrueAndIdNot(Long subjectId, Integer sequenceNumber, Long id);
}
