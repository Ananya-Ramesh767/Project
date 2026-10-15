package com.odysseus.backend.repository;

import com.odysseus.backend.domain.FacultyAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FacultyAvailabilityRepository extends JpaRepository<FacultyAvailability, Long> {
    List<FacultyAvailability> findByFacultyId(Long facultyId);
    List<FacultyAvailability> findByFacultyIdAndDayOfWeek(Long facultyId, String dayOfWeek);
    void deleteByFacultyId(Long facultyId);
}
