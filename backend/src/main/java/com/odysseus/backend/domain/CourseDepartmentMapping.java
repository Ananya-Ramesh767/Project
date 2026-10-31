package com.odysseus.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "course_department_mappings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseDepartmentMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 30)
    private String coursePrefix; // e.g. "CSC", "MAB", "MES", "CSS", "CYB", "SDA"

    private Long departmentId;

    @Column(nullable = false, length = 50)
    private String departmentKey; // e.g. "CSE", "MATH", "MECH", "UNMAPPED"

    @Column(nullable = false, length = 255)
    private String departmentName; // e.g. "Computer Science and Engineering"

    private String source; // "ADMIN_APPROVED", "VERIFIED_CURRICULUM", "INSTITUTIONAL_METADATA", "MANUAL"

    private String confidence; // "HIGH", "MEDIUM", "LOW"

    @Builder.Default
    @Column(length = 50)
    private String status = "MAPPED"; // "MAPPED", "PENDING_REVIEW", "UNMAPPED", "CONFLICT"

    @Builder.Default
    @Column(length = 50)
    private String approvalStatus = "APPROVED"; // "APPROVED", "PENDING", "REJECTED"

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
