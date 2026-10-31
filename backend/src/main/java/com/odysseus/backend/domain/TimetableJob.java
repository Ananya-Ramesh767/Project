package com.odysseus.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "timetable_jobs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimetableJob {

    @Id
    private String id; // UUID string

    @Column(nullable = false)
    private String status; // QUEUED, SOLVING, COMPLETED, FAILED, INFEASIBLE

    @Column(length = 2000)
    private String message;

    private String department;
    private Integer semester;

    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    @Builder.Default
    private int totalEntries = 0;
}
