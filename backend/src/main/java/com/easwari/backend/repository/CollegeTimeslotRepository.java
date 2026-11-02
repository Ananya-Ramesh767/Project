package com.easwari.backend.repository;

import com.easwari.backend.domain.CollegeTimeslot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CollegeTimeslotRepository extends JpaRepository<CollegeTimeslot, Long> {
    List<CollegeTimeslot> findByActiveTrue();
    List<CollegeTimeslot> findByDayOfWeekAndActiveTrue(String dayOfWeek);
}
