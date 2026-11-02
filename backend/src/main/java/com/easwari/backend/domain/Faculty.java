package com.easwari.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "faculty")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Faculty {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String employeeId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String department;

    private String departmentKey;
    private String sourceDocument;

    @Builder.Default
    private String verificationStatus = "APPROVED";

    private String designation;
    private String qualification;
    private Integer facultyNumber;
    private String specialization;
    private String subjectsHandled;

    @Builder.Default
    private String category = "TEACHING"; // TEACHING, HOD, LAB_TECHNICIAN, NON_TEACHING

    @Builder.Default
    private Boolean eligibleForTeaching = true;

    private Integer maxWeeklyLoad;

    @Builder.Default
    private boolean hod = false;

    @Builder.Default
    private boolean active = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "faculty_subject_eligibility",
        joinColumns = @JoinColumn(name = "faculty_id"),
        inverseJoinColumns = @JoinColumn(name = "subject_id")
    )
    @Builder.Default
    private Set<Subject> eligibleSubjects = new HashSet<>();

    public boolean isPrincipal() {
        if ("PRINCIPAL".equalsIgnoreCase(category)) return true;
        if (name != null) {
            String n = name.trim().toLowerCase();
            if (n.contains("deiva sundari") || n.contains("deivasundari")) return true;
        }
        if (designation != null && designation.toLowerCase().contains("principal")) {
            return true;
        }
        return false;
    }

    public boolean isHod() {
        if (hod) return true;
        if ("HOD".equalsIgnoreCase(category)) return true;
        if (designation != null) {
            String des = designation.trim().toLowerCase();
            return des.equals("hod") || des.contains("head of department") || des.contains("head of the department")
                    || des.matches("(?i).*\\b(hod|head)\\b.*")
                    || des.endsWith("&") || des.endsWith("and");
        }
        return false;
    }

    public boolean isEligibleForTeaching() {
        if (isPrincipal()) return false;
        if (isHod()) return false;
        if (!active) return false;
        if (category != null) {
            String cat = category.trim().toUpperCase();
            if (cat.equals("PRINCIPAL") || cat.equals("HOD") || cat.equals("LAB_TECHNICIAN") || cat.equals("NON_TEACHING")) {
                return false;
            }
        }
        if (designation != null) {
            String des = designation.trim().toLowerCase();
            if (des.contains("lab") || des.contains("programmer") || des.contains("technician")
                    || des.contains("assistant programmer") || des.contains("clerk") || des.contains("non-teaching")
                    || des.contains("supporting staff") || des.contains("attender")) {
                return false;
            }
        }
        return eligibleForTeaching != null && eligibleForTeaching;
    }
}
