package com.easwari.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalTime;

@Entity
@Table(name = "college_timeslots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollegeTimeslot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String dayOfWeek; // MONDAY..FRIDAY

    @Column(nullable = false)
    private Integer periodNumber; // 1..8 (or 0 for Break/Lunch)

    private String periodLabel; // P1, P2, BREAK, P3, P4, P5, LUNCH, P6, P7, P8

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    private boolean isBreak;
    private boolean isLunch;

    @Builder.Default
    private boolean active = true;
}
