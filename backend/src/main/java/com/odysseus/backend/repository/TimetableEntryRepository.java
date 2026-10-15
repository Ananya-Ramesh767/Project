package com.odysseus.backend.repository;

import com.odysseus.backend.domain.TimetableEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TimetableEntryRepository extends JpaRepository<TimetableEntry, Long> {
    List<TimetableEntry> findByJobIdOrderByDayOfWeekAscPeriodNumberAsc(String jobId);
}
