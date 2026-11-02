package com.easwari.backend.repository;

import com.easwari.backend.domain.CourseDepartmentMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseDepartmentMappingRepository extends JpaRepository<CourseDepartmentMapping, Long> {
    Optional<CourseDepartmentMapping> findByCoursePrefixIgnoreCase(String coursePrefix);
    List<CourseDepartmentMapping> findAllByOrderByCoursePrefixAsc();
    List<CourseDepartmentMapping> findByStatusIgnoreCase(String status);
    List<CourseDepartmentMapping> findByDepartmentKeyIgnoreCase(String departmentKey);
}
