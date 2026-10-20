package com.odysseus.backend.config;

import com.odysseus.backend.domain.Room;
import com.odysseus.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Initializes the institutional physical Room Master inventory per Master Prompt Section 6.
 *
 * Mechanical Block, Civil Block, Main Block, Academic Block, Electronics Block,
 * and the explicit Block 3 Ground Floor Chemistry & Physics Labs.
 *
 * Removes demo and sample subjects, demo batches, and demo offerings so that
 * the academic data tables start clean until the admin uploads curriculum documents.
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class SampleDataInitializer implements CommandLineRunner {

    private final RoomRepository roomRepository;
    private final SubjectRepository subjectRepository;
    private final BatchRepository batchRepository;
    private final SubjectOfferingRepository offeringRepository;
    private final TimetableAssignmentRepository assignmentRepository;
    private final TimetableEntryRepository entryRepository;
    private final FacultyRepository facultyRepository;

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void run(String... args) {
        log.info("[RoomInventoryInitializer] Initializing institutional physical Room Master inventory...");

        // Remove previous demo/test rooms if present
        cleanLegacyDemoRooms();

        // Remove demo subjects seeded earlier so Subjects table starts empty
        cleanDemoSubjectsAndOfferings();

        // Seed the exact institutional Room Master inventory
        seedMechanicalBlock();
        seedCivilBlock();
        seedMainBlock();
        seedAcademicBlock();
        seedElectronicsBlock();
        seedBlock3SpecialLabs();

        long activeRooms = roomRepository.findByActiveTrue().size();
        log.info("[RoomInventoryInitializer] Room Master inventory ready: {} total active rooms/labs.", activeRooms);
    }

    private void cleanLegacyDemoRooms() {
        assignmentRepository.deleteAll();
        entryRepository.deleteAll();
        offeringRepository.deleteAll();

        List<String> legacyCodes = List.of(
                "TEST-PERSIST-101", "CSE-A101", "CSE-A102", "CSE-A103", "CSE-LAB1",
                "A-101", "A-102", "A-201", "A-LAB-1",
                "BLO-301", "BLO-302", "BLO-401", "BLO-402", "BLO-403", "BLO-404", "BLO-405", "BLO-406", "BLO-407",
                "BLO-501", "BLO-502", "BLO-503", "BLO-504", "BLO-505", "BLO-506", "BLO-507"
        );
        for (String code : legacyCodes) {
            roomRepository.findByRoomCode(code).ifPresent(r -> {
                log.debug("Removing legacy/demo room {}", code);
                roomRepository.delete(r);
            });
        }
    }

    private void cleanDemoSubjectsAndOfferings() {
        // Master Prompt Section 3.1 & 14.1:
        // "Remove all prebuilt, sample, demo, and testing subjects from the application and database seed data.
        // The Subjects and Credits table must initially be empty until the admin uploads a curriculum document."
        List<String> demoCodes = List.of(
                "2311CSC301T", "2311CSC302T", "2311MAT301T", "2311CSC303T", "2311CSC304L",
                "2311CSE905T", "2321CSE906T", "2311CE0901T", "2311CSC501J", "2311CSC502J",
                "2311CSC503R", "2311SDSA501L", "2321LIB301", "2321CSC304R"
        );

        for (String code : demoCodes) {
            subjectRepository.findBySubjectCode(code).ifPresent(subject -> {
                // Delete child offerings
                offeringRepository.findAll().stream()
                        .filter(o -> o.getSubject() != null && o.getSubject().getId().equals(subject.getId()))
                        .forEach(offeringRepository::delete);

                // Delete child assignments
                assignmentRepository.findAll().stream()
                        .filter(a -> a.getSubject() != null && a.getSubject().getId().equals(subject.getId()))
                        .forEach(assignmentRepository::delete);

                entryRepository.findAll().stream()
                        .filter(e -> e.getSubjectId() != null && e.getSubjectId().equals(subject.getId()))
                        .forEach(entryRepository::delete);

                // Unlink from faculty
                facultyRepository.findAll().forEach(f -> {
                    if (f.getEligibleSubjects() != null && f.getEligibleSubjects().removeIf(s -> s.getId().equals(subject.getId()))) {
                        facultyRepository.save(f);
                    }
                });

                subjectRepository.delete(subject);
            });
        }

        // Clean demo batches if only demo batches exist
        List<String> demoBatchNames = List.of("CSE-A", "CSE-B", "CSE-Sem3-A", "CSE-Sem3-B");
        for (String bName : demoBatchNames) {
            batchRepository.findAll().stream()
                    .filter(b -> bName.equalsIgnoreCase(b.getBatchName()))
                    .forEach(batch -> {
                        offeringRepository.findByBatchIdAndActiveTrue(batch.getId()).forEach(offeringRepository::delete);
                        assignmentRepository.deleteByBatchId(batch.getId());
                        batchRepository.delete(batch);
                    });
        }

        // Clean demo faculty (employeeId starting with F-DEMO-)
        facultyRepository.findAll().stream()
                .filter(f -> f.getEmployeeId() != null && f.getEmployeeId().startsWith("F-DEMO-"))
                .forEach(facultyRepository::delete);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // A. Mechanical Block
    // - First floor: 1 lab.
    // - Second floor: 3 classrooms.
    // - Third floor: 3 classrooms.
    // - Fourth floor: 4 classrooms.
    // - Fifth floor: 2 classrooms.
    // ─────────────────────────────────────────────────────────────────────────
    private void seedMechanicalBlock() {
        String b = "Mechanical Block";
        saveRoom("MECH-F1-LAB1", "Mechanical Block - First Floor - Mechanical Lab", "LAB", 60, b, 1, "Mechanical Engineering");

        saveRoom("MECH-F2-CR1", "Mechanical Block - Second Floor - Classroom 1", "LECTURE_HALL", 70, b, 2, "Mechanical Engineering");
        saveRoom("MECH-F2-CR2", "Mechanical Block - Second Floor - Classroom 2", "LECTURE_HALL", 70, b, 2, "Mechanical Engineering");
        saveRoom("MECH-F2-CR3", "Mechanical Block - Second Floor - Classroom 3", "LECTURE_HALL", 70, b, 2, "Mechanical Engineering");

        saveRoom("MECH-F3-CR1", "Mechanical Block - Third Floor - Classroom 1", "LECTURE_HALL", 70, b, 3, "Mechanical Engineering");
        saveRoom("MECH-F3-CR2", "Mechanical Block - Third Floor - Classroom 2", "LECTURE_HALL", 70, b, 3, "Mechanical Engineering");
        saveRoom("MECH-F3-CR3", "Mechanical Block - Third Floor - Classroom 3", "LECTURE_HALL", 70, b, 3, "Mechanical Engineering");

        saveRoom("MECH-F4-CR1", "Mechanical Block - Fourth Floor - Classroom 1", "LECTURE_HALL", 70, b, 4, "Mechanical Engineering");
        saveRoom("MECH-F4-CR2", "Mechanical Block - Fourth Floor - Classroom 2", "LECTURE_HALL", 70, b, 4, "Mechanical Engineering");
        saveRoom("MECH-F4-CR3", "Mechanical Block - Fourth Floor - Classroom 3", "LECTURE_HALL", 70, b, 4, "Mechanical Engineering");
        saveRoom("MECH-F4-CR4", "Mechanical Block - Fourth Floor - Classroom 4", "LECTURE_HALL", 70, b, 4, "Mechanical Engineering");

        saveRoom("MECH-F5-CR1", "Mechanical Block - Fifth Floor - Classroom 1", "LECTURE_HALL", 70, b, 5, "Mechanical Engineering");
        saveRoom("MECH-F5-CR2", "Mechanical Block - Fifth Floor - Classroom 2", "LECTURE_HALL", 70, b, 5, "Mechanical Engineering");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // B. Civil Block
    // - First floor: no classrooms specified.
    // - Second floor: 4 classrooms.
    // - Third floor: 5 classrooms.
    // - Fourth floor: 5 classrooms.
    // - Fifth floor: 5 classrooms.
    // - Sixth floor: 1 gallery hall.
    // - Seventh floor: 2 MBA halls (MBA Hall 1 and MBA Hall 2) and 4 classrooms.
    // ─────────────────────────────────────────────────────────────────────────
    private void seedCivilBlock() {
        String b = "Civil Block";
        for (int i = 1; i <= 4; i++) {
            saveRoom("CIVIL-F2-CR" + i, "Civil Block - Second Floor - Classroom " + i, "LECTURE_HALL", 70, b, 2, "Civil Engineering");
        }
        for (int i = 1; i <= 5; i++) {
            saveRoom("CIVIL-F3-CR" + i, "Civil Block - Third Floor - Classroom " + i, "LECTURE_HALL", 70, b, 3, "Civil Engineering");
            saveRoom("CIVIL-F4-CR" + i, "Civil Block - Fourth Floor - Classroom " + i, "LECTURE_HALL", 70, b, 4, "Civil Engineering");
            saveRoom("CIVIL-F5-CR" + i, "Civil Block - Fifth Floor - Classroom " + i, "LECTURE_HALL", 70, b, 5, "Civil Engineering");
        }
        saveRoom("CIVIL-F6-GH1", "Civil Block - Sixth Floor - Gallery Hall", "GALLERY_HALL", 120, b, 6, "Civil Engineering");

        saveRoom("CIVIL-F7-MBA1", "Civil Block - Seventh Floor - MBA Hall 1", "SEMINAR_HALL", 100, b, 7, "MBA");
        saveRoom("CIVIL-F7-MBA2", "Civil Block - Seventh Floor - MBA Hall 2", "SEMINAR_HALL", 100, b, 7, "MBA");
        for (int i = 1; i <= 4; i++) {
            saveRoom("CIVIL-F7-CR" + i, "Civil Block - Seventh Floor - Classroom " + i, "LECTURE_HALL", 70, b, 7, "Civil Engineering");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // C. Main Block
    // - First floor: 3 classrooms.
    // - Second floor: 4 classrooms.
    // - Third floor: 3 labs and 4 classrooms.
    // - Fourth floor: 8 classrooms.
    // - Fifth floor: 8 classrooms.
    // ─────────────────────────────────────────────────────────────────────────
    private void seedMainBlock() {
        String b = "Main Block";
        for (int i = 1; i <= 3; i++) {
            saveRoom("MAIN-F1-CR" + i, "Main Block - First Floor - Classroom " + i, "LECTURE_HALL", 70, b, 1, "General");
        }
        for (int i = 1; i <= 4; i++) {
            saveRoom("MAIN-F2-CR" + i, "Main Block - Second Floor - Classroom " + i, "LECTURE_HALL", 70, b, 2, "General");
        }
        for (int i = 1; i <= 3; i++) {
            saveRoom("MAIN-F3-LAB" + i, "Main Block - Third Floor - Specialized Lab " + i, "LAB", 60, b, 3, "Computer Science Engineering");
        }
        for (int i = 1; i <= 4; i++) {
            saveRoom("MAIN-F3-CR" + i, "Main Block - Third Floor - Classroom " + i, "LECTURE_HALL", 70, b, 3, "General");
        }
        for (int i = 1; i <= 8; i++) {
            saveRoom("MAIN-F4-CR" + i, "Main Block - Fourth Floor - Classroom " + i, "LECTURE_HALL", 70, b, 4, "General");
            saveRoom("MAIN-F5-CR" + i, "Main Block - Fifth Floor - Classroom " + i, "LECTURE_HALL", 70, b, 5, "General");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // D. Academic Block
    // - Third floor: 8 classrooms.
    // - Fourth floor: 8 classrooms.
    // - Fifth floor: 8 classrooms.
    // ─────────────────────────────────────────────────────────────────────────
    private void seedAcademicBlock() {
        String b = "Academic Block";
        for (int i = 1; i <= 8; i++) {
            saveRoom("ACAD-F3-CR" + i, "Academic Block - Third Floor - Classroom " + i, "LECTURE_HALL", 70, b, 3, "Computer Science Engineering");
            saveRoom("ACAD-F4-CR" + i, "Academic Block - Fourth Floor - Classroom " + i, "LECTURE_HALL", 70, b, 4, "Computer Science Engineering");
            saveRoom("ACAD-F5-CR" + i, "Academic Block - Fifth Floor - Classroom " + i, "LECTURE_HALL", 70, b, 5, "Computer Science Engineering");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // E. Electronics Block
    // - First floor: 3 classrooms and 1 lab.
    // - Second floor: 2 labs.
    // - Third floor: 5 classrooms.
    // - Fourth floor: 5 classrooms.
    // - Fifth floor: no rooms specified.
    // ─────────────────────────────────────────────────────────────────────────
    private void seedElectronicsBlock() {
        String b = "Electronics Block";
        for (int i = 1; i <= 3; i++) {
            saveRoom("ELEC-F1-CR" + i, "Electronics Block - First Floor - Classroom " + i, "LECTURE_HALL", 70, b, 1, "Electronics & Communication Engineering");
        }
        saveRoom("ELEC-F1-LAB1", "Electronics Block - First Floor - Electronics Lab 1", "LAB", 60, b, 1, "Electronics & Communication Engineering");

        saveRoom("ELEC-F2-LAB1", "Electronics Block - Second Floor - Specialized Lab 1", "LAB", 60, b, 2, "Electronics & Communication Engineering");
        saveRoom("ELEC-F2-LAB2", "Electronics Block - Second Floor - Specialized Lab 2", "LAB", 60, b, 2, "Electronics & Communication Engineering");

        for (int i = 1; i <= 5; i++) {
            saveRoom("ELEC-F3-CR" + i, "Electronics Block - Third Floor - Classroom " + i, "LECTURE_HALL", 70, b, 3, "Electronics & Communication Engineering");
            saveRoom("ELEC-F4-CR" + i, "Electronics Block - Fourth Floor - Classroom " + i, "LECTURE_HALL", 70, b, 4, "Electronics & Communication Engineering");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Special lab location
    // The Chemistry Lab and Physics Lab are both in Block 3, Ground Floor.
    // Register and allocate these labs according to this explicit location.
    // ─────────────────────────────────────────────────────────────────────────
    private void seedBlock3SpecialLabs() {
        String b = "Block 3";
        saveRoom("B3-G-CHEM", "Block 3 - Ground Floor - Chemistry Lab", "LAB", 60, b, 0, "Chemistry");
        saveRoom("B3-G-PHYS", "Block 3 - Ground Floor - Physics Lab", "LAB", 60, b, 0, "Physics");
    }

    private void saveRoom(String code, String name, String type, int cap, String block, int floor, String dept) {
        if (!roomRepository.existsByRoomCode(code)) {
            roomRepository.save(Room.builder()
                    .roomCode(code)
                    .roomName(name)
                    .roomType(type)
                    .capacity(cap)
                    .blockName(block)
                    .floorNumber(floor)
                    .department(dept)
                    .active(true)
                    .build());
        }
    }
}
