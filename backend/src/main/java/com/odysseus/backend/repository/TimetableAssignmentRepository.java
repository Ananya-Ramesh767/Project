package com.odysseus.backend.repository;

import com.odysseus.backend.domain.TimetableAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TimetableAssignmentRepository extends JpaRepository<TimetableAssignment, Long> {
    List<TimetableAssignment> findByBatchIdAndActiveTrue(Long batchId);
    List<TimetableAssignment> findByFacultyIdAndActiveTrue(Long facultyId);
    List<TimetableAssignment> findByDayOfWeekAndActiveTrue(String dayOfWeek);
    List<TimetableAssignment> findByRoomIdAndActiveTrue(Long roomId);
    List<TimetableAssignment> findByActiveTrue();
    void deleteByBatchId(Long batchId);
}
