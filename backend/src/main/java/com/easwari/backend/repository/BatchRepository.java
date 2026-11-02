package com.easwari.backend.repository;

import com.easwari.backend.domain.Batch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BatchRepository extends JpaRepository<Batch, Long> {
    List<Batch> findByActiveTrue();
    Optional<Batch> findByDepartmentAndSemesterAndSection(String department, Integer semester, String section);
    List<Batch> findByDepartmentAndSemesterAndActiveTrue(String department, Integer semester);
}
