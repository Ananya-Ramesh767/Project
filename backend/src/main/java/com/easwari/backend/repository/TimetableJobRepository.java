package com.easwari.backend.repository;

import com.easwari.backend.domain.TimetableJob;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimetableJobRepository extends JpaRepository<TimetableJob, String> {
}
