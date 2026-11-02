package com.easwari.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "academic_calendars")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcademicCalendar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String academicYear;

    private Integer semester;
    private LocalDate startDate;
    private LocalDate endDate;

    private String SaturdayRule; // ALL_HOLIDAY, ALL_WORKING, ALTERNATE, CUSTOM
    private String SundayRule;   // ALL_HOLIDAY by default

    @Builder.Default
    private boolean active = true;

    @OneToMany(mappedBy = "calendar", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<CalendarHoliday> holidays = new ArrayList<>();
}
