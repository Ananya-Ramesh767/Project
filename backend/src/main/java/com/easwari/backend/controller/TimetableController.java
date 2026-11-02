package com.easwari.backend.controller;

import com.easwari.backend.domain.*;
import com.easwari.backend.dto.TimetableValidationResult;
import com.easwari.backend.repository.*;
import com.easwari.backend.service.TimetableValidationService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/timetable")
@RequiredArgsConstructor
public class TimetableController {

    private final TimetableAssignmentRepository timetableRepository;
    private final TimetableValidationService validationService;
    private final BatchRepository batchRepository;
    private final SubjectRepository subjectRepository;
    private final FacultyRepository facultyRepository;
    private final RoomRepository roomRepository;
    private final RoomChangeHistoryRepository roomChangeHistoryRepository;
    private final TimetableEntryRepository entryRepository;

    @GetMapping
    public ResponseEntity<List<TimetableAssignment>> getAllTimetables() {
        return ResponseEntity.ok(timetableRepository.findByActiveTrue());
    }

    @GetMapping("/batch/{batchId}")
    public ResponseEntity<List<TimetableAssignment>> getTimetableForBatch(@PathVariable Long batchId) {
        return ResponseEntity.ok(timetableRepository.findByBatchIdAndActiveTrue(batchId));
    }

    @GetMapping("/faculty/{facultyId}")
    public ResponseEntity<List<TimetableAssignment>> getTimetableForFaculty(@PathVariable Long facultyId) {
        return ResponseEntity.ok(timetableRepository.findByFacultyIdAndActiveTrue(facultyId));
    }

    @PostMapping("/validate")
    public ResponseEntity<TimetableValidationResult> validateAssignment(@RequestBody TimetableAssignment proposed) {
        TimetableValidationResult result = validationService.validateProposedAssignment(proposed, proposed.getId());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/assignment")
    public ResponseEntity<?> saveAssignment(@RequestBody TimetableAssignment proposed) {
        TimetableValidationResult result = validationService.validateProposedAssignment(proposed, proposed.getId());
        if (!result.isValid()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }

        Batch batch = batchRepository.findById(proposed.getBatch().getId())
                .orElseThrow(() -> new IllegalArgumentException("Batch not found"));
        Subject subject = subjectRepository.findById(proposed.getSubject().getId())
                .orElseThrow(() -> new IllegalArgumentException("Subject not found"));
        Faculty faculty = (proposed.getFaculty() != null && proposed.getFaculty().getId() != null)
                ? facultyRepository.findById(proposed.getFaculty().getId()).orElse(null)
                : null;
        Room room = roomRepository.findById(proposed.getRoom().getId())
                .orElseThrow(() -> new IllegalArgumentException("Room not found"));

        proposed.setBatch(batch);
        proposed.setSubject(subject);
        proposed.setFaculty(faculty);
        proposed.setRoom(room);
        proposed.setLabBlock(subject.isRequiresLab() || (proposed.getDurationPeriods() != null && proposed.getDurationPeriods() > 1));
        proposed.setActive(true);

        TimetableAssignment saved = timetableRepository.save(proposed);
        return ResponseEntity.ok(saved);
    }

    @DeleteMapping("/assignment/{id}")
    public ResponseEntity<?> deleteAssignment(@PathVariable Long id) {
        return timetableRepository.findById(id).map(assignment -> {
            timetableRepository.delete(assignment);
            return ResponseEntity.ok(Map.of("message", "Assignment deleted successfully"));
        }).orElse(ResponseEntity.notFound().build());
    }

    @Data
    public static class RoomChangeRequest {
        private Long assignmentId;
        private Long newRoomId;
        private String reason;
        private String changedBy;
    }

    @PostMapping("/change-room")
    public ResponseEntity<?> changeClassroom(@RequestBody RoomChangeRequest request) {
        TimetableAssignment assignment = timetableRepository.findById(request.getAssignmentId())
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
        Room newRoom = roomRepository.findById(request.getNewRoomId())
                .orElseThrow(() -> new IllegalArgumentException("Target room not found"));

        Room originalRoom = assignment.getRoom();

        // Check room capacity
        if (newRoom.getCapacity() != null && assignment.getBatch().getStrength() != null 
                && newRoom.getCapacity() < assignment.getBatch().getStrength()) {
            return ResponseEntity.badRequest().body(Map.of("error", 
                    "New room capacity (" + newRoom.getCapacity() + ") is insufficient for section strength (" + assignment.getBatch().getStrength() + ")"));
        }

        // Record change history
        RoomChangeHistory history = RoomChangeHistory.builder()
                .batch(assignment.getBatch())
                .subject(assignment.getSubject())
                .originalRoom(originalRoom)
                .newRoom(newRoom)
                .effectiveDate(LocalDate.now())
                .reason(request.getReason() != null ? request.getReason() : "Administrative adjustment")
                .changedBy(request.getChangedBy() != null ? request.getChangedBy() : "Admin")
                .timestamp(LocalDateTime.now())
                .active(true)
                .build();
        roomChangeHistoryRepository.save(history);

        // Update assignment
        assignment.setRoom(newRoom);
        timetableRepository.save(assignment);

        // Update corresponding entries
        if (entryRepository != null && assignment.getBatch() != null) {
            entryRepository.findAll().stream()
                    .filter(e -> java.util.Objects.equals(e.getBatchId(), assignment.getBatch().getId())
                            && java.util.Objects.equals(e.getDayOfWeek(), assignment.getDayOfWeek())
                            && java.util.Objects.equals(e.getPeriodNumber(), assignment.getPeriodNumber()))
                    .forEach(e -> {
                        e.setRoomId(newRoom.getId());
                        e.setRoomCode(newRoom.getRoomCode());
                        entryRepository.save(e);
                    });
        }

        // Update batch assigned room
        Batch batch = assignment.getBatch();
        if (batch != null && (batch.getAssignedRoomCode() == null || (originalRoom != null && batch.getAssignedRoomCode().equals(originalRoom.getRoomCode())))) {
            batch.setAssignedRoomCode(newRoom.getRoomCode());
            batch.setAssignedBlockName(newRoom.getBlockName());
            batch.setAssignedFloorNumber(newRoom.getFloorNumber());
            batchRepository.save(batch);
            newRoom.setAssignedSection(batch.getBatchName());
            roomRepository.save(newRoom);
        }

        return ResponseEntity.ok(Map.of(
                "message", "Classroom successfully changed to " + newRoom.getRoomCode(),
                "historyId", history.getId(),
                "assignment", assignment
        ));
    }

    @PostMapping("/restore-room")
    public ResponseEntity<?> restoreUsualClassroom(@RequestBody Map<String, Long> payload) {
        Long historyId = payload.get("historyId");
        if (historyId == null) return ResponseEntity.badRequest().body(Map.of("error", "historyId is required"));

        RoomChangeHistory history = roomChangeHistoryRepository.findById(historyId)
                .orElseThrow(() -> new IllegalArgumentException("History entry not found"));

        // Find current assignment for this batch and subject
        List<TimetableAssignment> assignments = timetableRepository.findByBatchIdAndActiveTrue(history.getBatch().getId());
        for (TimetableAssignment a : assignments) {
            if (history.getSubject() != null && a.getSubject().getId().equals(history.getSubject().getId())) {
                a.setRoom(history.getOriginalRoom());
                timetableRepository.save(a);
            }
        }

        Batch batch = history.getBatch();
        if (batch != null && history.getOriginalRoom() != null) {
            batch.setAssignedRoomCode(history.getOriginalRoom().getRoomCode());
            batch.setAssignedBlockName(history.getOriginalRoom().getBlockName());
            batch.setAssignedFloorNumber(history.getOriginalRoom().getFloorNumber());
            batchRepository.save(batch);
        }

        history.setActive(false);
        roomChangeHistoryRepository.save(history);

        return ResponseEntity.ok(Map.of(
                "message", "Restored to usual classroom: " + history.getOriginalRoom().getRoomCode()
        ));
    }

    @GetMapping("/room-history/{batchId}")
    public ResponseEntity<List<RoomChangeHistory>> getRoomChangeHistory(@PathVariable Long batchId) {
        return ResponseEntity.ok(roomChangeHistoryRepository.findByBatchIdOrderByTimestampDesc(batchId));
    }
}
