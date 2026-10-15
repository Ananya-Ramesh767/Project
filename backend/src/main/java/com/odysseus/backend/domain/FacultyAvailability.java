package com.odysseus.backend.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "faculty_availability")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FacultyAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "faculty_id", nullable = false)
    private Faculty faculty;

    @Column(nullable = false)
    private String dayOfWeek; // MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY

    @Column(nullable = false)
    private Integer periodNumber; // 1 through 8

    @Builder.Default
    @Column(nullable = false)
    private boolean available = true;

    @Builder.Default
    @Column(nullable = false)
    private boolean preferred = false;
}
