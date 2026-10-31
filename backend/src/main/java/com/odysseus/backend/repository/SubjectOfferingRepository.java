package com.odysseus.backend.repository;

import com.odysseus.backend.domain.SubjectOffering;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface SubjectOfferingRepository extends JpaRepository<SubjectOffering, Long> {
    List<SubjectOffering> findByActiveTrue();
    List<SubjectOffering> findByBatchIdAndActiveTrue(Long batchId);
    boolean existsByBatchIdAndSubjectIdAndActiveTrue(Long batchId, Long subjectId);
    List<SubjectOffering> findByBatchIdAndSubjectId(Long batchId, Long subjectId);
    List<SubjectOffering> findByAssignedFacultyIdAndActiveTrue(Long facultyId);

    @Modifying
    @Transactional
    @Query("UPDATE SubjectOffering o SET o.assignedFaculty = NULL WHERE o.active = true")
    void resetAllAssignments();
}
