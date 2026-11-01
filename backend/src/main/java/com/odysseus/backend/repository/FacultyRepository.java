package com.odysseus.backend.repository;

import com.odysseus.backend.domain.Faculty;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FacultyRepository extends JpaRepository<Faculty, Long> {
    List<Faculty> findByActiveTrue();
    Optional<Faculty> findByEmailIgnoreCaseAndActiveTrue(String email);
    Optional<Faculty> findByEmployeeId(String employeeId);
    boolean existsByEmployeeId(String employeeId);
    boolean existsByEmployeeIdAndIdNot(String employeeId, Long id);
}
