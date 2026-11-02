package com.easwari.backend.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String roomCode;

    @Column(nullable = false)
    private String roomName;

    @Column(nullable = false)
    private String roomType; // LECTURE_HALL, LAB

    @Column(nullable = false)
    private Integer capacity;

    private String blockName;
    private Integer floorNumber;
    private String department;

    // Year + Department + Section linkage (Room Master requirements)
    private Integer yearOfStudy;      // e.g. 1, 2, 3, 4
    private String assignedSection;   // e.g. "A", "B", "CSE-A"

    @Builder.Default
    private boolean active = true;
}
