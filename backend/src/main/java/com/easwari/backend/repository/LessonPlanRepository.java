package com.easwari.backend.repository;

import com.easwari.backend.domain.LessonPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LessonPlanRepository extends JpaRepository<LessonPlan, Long> {
    Optional<LessonPlan> findBySubjectOfferingIdAndAcademicCalendarIdAndActiveTrue(Long subjectOfferingId, Long academicCalendarId);
    Optional<LessonPlan> findFirstBySubjectOfferingIdAndActiveTrueOrderByGeneratedAtDesc(Long subjectOfferingId);
    List<LessonPlan> findBySubjectOffering_AssignedFaculty_IdAndActiveTrue(Long facultyId);
    List<LessonPlan> findByActiveTrue();
}
