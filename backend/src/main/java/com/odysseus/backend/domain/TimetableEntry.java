package com.odysseus.backend.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "timetable_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimetableEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String jobId;

    // Timeslot info
    @Column(nullable = false)
    private String dayOfWeek;

    @Column(nullable = false)
    private Integer periodNumber;

    private String periodLabel;
    private String startTime;
    private String endTime;

    // Batch info
    @Column(nullable = false)
    private Long batchId;
    private String batchName;

    // Subject info
    @Column(nullable = false)
    private Long subjectId;
    private String subjectCode;
    private String subjectName;

    // Faculty info
    @Column(nullable = false)
    private Long facultyId;
    private String facultyName;

    // Room info
    @Column(nullable = false)
    private Long roomId;
    private String roomCode;

    // Session type: THEORY, LAB, TUTORIAL
    @Column(nullable = false)
    private String sessionType;
}
