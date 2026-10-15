package com.odysseus.backend.repository;

import com.odysseus.backend.domain.TimetableJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimetableJobRepository extends JpaRepository<TimetableJob, String> {
}
