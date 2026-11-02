package com.easwari.backend.repository;

import com.easwari.backend.domain.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, Long> {
    Optional<Department> findByDepartmentKeyIgnoreCase(String departmentKey);
    Optional<Department> findByDepartmentNameIgnoreCase(String departmentName);
    List<Department> findByActiveTrue();
    List<Department> findAllByOrderByDepartmentKeyAsc();
}
