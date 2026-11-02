package com.easwari.backend.repository;

import com.easwari.backend.domain.Subject;
import com.easwari.backend.domain.SyllabusUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SyllabusUnitRepository extends JpaRepository<SyllabusUnit, Long> {
    List<SyllabusUnit> findBySubjectIdAndActiveTrueOrderByUnitNumberAsc(Long subjectId);
    List<SyllabusUnit> findBySubjectIdAndSessionTypeAndActiveTrueOrderByUnitNumberAsc(Long subjectId, String sessionType);
    List<SyllabusUnit> findBySubjectAndSessionTypeAndActiveTrueOrderByUnitNumberAsc(Subject subject, String sessionType);
    boolean existsBySubjectIdAndSessionTypeAndUnitNumberAndActiveTrue(Long subjectId, String sessionType, Integer unitNumber);
    boolean existsBySubjectIdAndSessionTypeAndUnitNumberAndActiveTrueAndIdNot(Long subjectId, String sessionType, Integer unitNumber, Long id);
    java.util.Optional<SyllabusUnit> findBySubjectIdAndSessionTypeAndUnitNumberAndActiveTrue(Long subjectId, String sessionType, Integer unitNumber);
}


