package com.easwari.backend.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "subject_offerings", uniqueConstraints = @UniqueConstraint(columnNames = {"batch_id", "subject_id", "active"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubjectOffering {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "batch_id", nullable = false)
    private Batch batch;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "assigned_faculty_id")
    private Faculty assignedFaculty;

    private String academicYear;
    private Integer semester;

    @Builder.Default
    private boolean active = true;
}
