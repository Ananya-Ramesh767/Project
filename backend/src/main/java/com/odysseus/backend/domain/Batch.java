package com.odysseus.backend.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "batches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Batch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String batchName;

    private String section;

    @Column(nullable = false)
    private String department;

    private Integer semester;

    @Column(nullable = false)
    private Integer strength;

    private String academicYear;

    // Room assignment — Block + Floor + Classroom (links to Room Master)
    private String assignedBlockName;   // e.g. "Academic Block A"
    private Integer assignedFloorNumber; // e.g. 1
    private String assignedRoomCode;    // e.g. "A101"

    @Builder.Default
    private boolean active = true;
}
