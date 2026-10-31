package com.odysseus.backend.service;

import com.odysseus.backend.domain.*;
import com.odysseus.backend.dto.TimetableValidationResult;
import com.odysseus.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class TimetableValidationService {

    private final TimetableAssignmentRepository timetableRepository;
    private final FacultyRepository facultyRepository;
    private final SubjectRepository subjectRepository;
    private final RoomRepository roomRepository;
    private final BatchRepository batchRepository;
    private final FacultyAvailabilityRepository availabilityRepository;

    /**
     * Validates a single proposed assignment against existing persisted assignments.
     */
    public TimetableValidationResult validateProposedAssignment(TimetableAssignment proposed, Long excludeAssignmentId) {
        if (proposed.getBatch() == null || proposed.getBatch().getId() == null) {
            return TimetableValidationResult.failure("MISSING_DATA", "Batch/Section is required.");
        }
        if (proposed.getSubject() == null || proposed.getSubject().getId() == null) {
            return TimetableValidationResult.failure("MISSING_DATA", "Subject is required.");
        }
        if (proposed.getFaculty() == null || proposed.getFaculty().getId() == null) {
            return TimetableValidationResult.failure("MISSING_DATA", "Faculty is required.");
        }
        if (proposed.getRoom() == null || proposed.getRoom().getId() == null) {
            return TimetableValidationResult.failure("MISSING_DATA", "Room is required.");
        }
        if (proposed.getDayOfWeek() == null || proposed.getDayOfWeek().trim().isEmpty()) {
            return TimetableValidationResult.failure("INVALID_TIMESLOT", "Day of week is required.");
        }
        if (proposed.getPeriodNumber() == null || proposed.getPeriodNumber() < 1 || proposed.getPeriodNumber() > 8) {
            return TimetableValidationResult.failure("INVALID_TIMESLOT", "Period number must be between 1 and 8.");
        }

        int duration = proposed.getDurationPeriods() != null && proposed.getDurationPeriods() > 0 
                ? proposed.getDurationPeriods() : 1;
        int proposedStart = proposed.getPeriodNumber();
        int proposedEnd = proposedStart + duration - 1;

        if (proposedEnd > 8) {
            return TimetableValidationResult.failure("INVALID_TIMESLOT", 
                    "Assigned duration extends beyond maximum period 8 (P" + proposedStart + " to P" + proposedEnd + ").");
        }

        // Lab continuity break-crossing checks:
        // Morning break is between P2 and P3; Lunch is between P5 and P6.
        if (proposedStart <= 2 && proposedEnd >= 3) {
            return TimetableValidationResult.failure("LAB_CONTINUITY", 
                    "Continuous class block crosses Morning Break (09:55 - 10:10). Labs must not cross breaks.");
        }
        if (proposedStart <= 5 && proposedEnd >= 6) {
            return TimetableValidationResult.failure("LAB_CONTINUITY", 
                    "Continuous class block crosses Lunch Break (12:40 - 13:30). Labs must not cross lunch.");
        }

        Batch batch = batchRepository.findById(proposed.getBatch().getId()).orElse(proposed.getBatch());
        Subject subject = subjectRepository.findById(proposed.getSubject().getId()).orElse(proposed.getSubject());
        Faculty faculty = facultyRepository.findById(proposed.getFaculty().getId()).orElse(proposed.getFaculty());
        Room room = roomRepository.findById(proposed.getRoom().getId()).orElse(proposed.getRoom());

        // Check 1: Room Capacity Constraint
        if (room.getCapacity() != null && batch.getStrength() != null && room.getCapacity() < batch.getStrength()) {
            return TimetableValidationResult.failure("ROOM_CAPACITY", 
                    String.format("Room [%s] capacity (%d) is insufficient for section [%s] strength (%d).",
                            room.getRoomCode(), room.getCapacity(), batch.getBatchName(), batch.getStrength()));
        }

        // Check 2: Room Type Constraint
        boolean isLabSession = proposed.isLabBlock() || "LAB".equalsIgnoreCase(proposed.getActivityType()) || "LAB".equalsIgnoreCase(subject.getSubjectType());
        if (isLabSession && !isLabRoom(room.getRoomType())) {
            return TimetableValidationResult.failure("ROOM_TYPE", 
                    String.format("Subject [%s] lab session requires a LAB room, but room [%s] is of type '%s'.",
                            subject.getSubjectCode(), room.getRoomCode(), room.getRoomType()));
        }

        // Check 3: Faculty Eligibility Constraint
        boolean isEligible = faculty.getEligibleSubjects().stream()
                .anyMatch(s -> Objects.equals(s.getId(), subject.getId()) 
                        || (s.getSubjectCode() != null && s.getSubjectCode().equalsIgnoreCase(subject.getSubjectCode())));
        if (!isEligible) {
            return TimetableValidationResult.failure("FACULTY_ELIGIBILITY", 
                    String.format("Faculty [%s (%s)] is not eligible to teach subject [%s - %s].",
                            faculty.getName(), faculty.getEmployeeId(), subject.getSubjectCode(), subject.getSubjectName()));
        }

        // Check 4: Faculty Availability Constraint
        List<FacultyAvailability> unavailabilities = availabilityRepository.findByFacultyIdAndDayOfWeek(faculty.getId(), proposed.getDayOfWeek());
        for (FacultyAvailability fa : unavailabilities) {
            if (!fa.isAvailable()) {
                if (fa.getPeriodNumber() >= proposedStart && fa.getPeriodNumber() <= proposedEnd) {
                    return TimetableValidationResult.failure("FACULTY_UNAVAILABLE",
                            String.format("Faculty [%s] is marked UNAVAILABLE on %s at period P%d.",
                                    faculty.getName(), proposed.getDayOfWeek(), fa.getPeriodNumber()));
                }
            }
        }

        // Fetch existing active assignments
        List<TimetableAssignment> existingAssignments = timetableRepository.findByDayOfWeekAndActiveTrue(proposed.getDayOfWeek());

        for (TimetableAssignment existing : existingAssignments) {
            if (excludeAssignmentId != null && existing.getId() != null && existing.getId().equals(excludeAssignmentId)) {
                continue;
            }

            int existStart = existing.getPeriodNumber();
            int existDuration = existing.getDurationPeriods() != null && existing.getDurationPeriods() > 0 
                    ? existing.getDurationPeriods() : 1;
            int existEnd = existStart + existDuration - 1;

            boolean overlaps = Math.max(proposedStart, existStart) <= Math.min(proposedEnd, existEnd);
            if (!overlaps) {
                continue;
            }

            // Conflict Check 5: Section Conflict
            if (Objects.equals(existing.getBatch().getId(), batch.getId())) {
                return TimetableValidationResult.failure("SECTION_CONFLICT", 
                        String.format("Section [%s] already has subject [%s] scheduled on %s at period P%d-P%d. Section double-booking is prohibited.",
                                batch.getBatchName(), existing.getSubject().getSubjectName(), proposed.getDayOfWeek(), existStart, existEnd));
            }

            // Conflict Check 6: Faculty Conflict
            if (Objects.equals(existing.getFaculty().getId(), faculty.getId())) {
                return TimetableValidationResult.failure("FACULTY_CONFLICT", 
                        String.format("Faculty [%s] is already scheduled teaching section [%s] on %s at period P%d-P%d. Faculty double-booking is prohibited.",
                                faculty.getName(), existing.getBatch().getBatchName(), proposed.getDayOfWeek(), existStart, existEnd));
            }

            // Conflict Check 7: Room Conflict
            if (Objects.equals(existing.getRoom().getId(), room.getId())) {
                return TimetableValidationResult.failure("ROOM_CONFLICT", 
                        String.format("Room [%s] is already occupied by section [%s] on %s at period P%d-P%d. Room double-booking is prohibited.",
                                room.getRoomCode(), existing.getBatch().getBatchName(), proposed.getDayOfWeek(), existStart, existEnd));
            }
        }

        // Conflict Check 8: Faculty Maximum Weekly Workload
        if (faculty.getMaxWeeklyLoad() != null && faculty.getMaxWeeklyLoad() > 0) {
            List<TimetableAssignment> facultyWeeklyAssignments = timetableRepository.findByFacultyIdAndActiveTrue(faculty.getId());
            int currentWorkload = facultyWeeklyAssignments.stream()
                    .filter(a -> excludeAssignmentId == null || !Objects.equals(a.getId(), excludeAssignmentId))
                    .mapToInt(a -> a.getDurationPeriods() != null ? a.getDurationPeriods() : 1)
                    .sum();

            if (currentWorkload + duration > faculty.getMaxWeeklyLoad()) {
                return TimetableValidationResult.failure("WORKLOAD_EXCEEDED", 
                        String.format("Assigning this %d-period slot increases faculty [%s] workload to %d periods, exceeding max weekly limit of %d periods.",
                                duration, faculty.getName(), currentWorkload + duration, faculty.getMaxWeeklyLoad()));
            }
        }

        return TimetableValidationResult.success("Assignment is fully valid and satisfies all constraints.");
    }

    /**
     * Batch validation of an entire generated schedule before persistence.
     */
    public TimetableValidationResult validateEntireSchedule(List<TimetableAssignment> assignments) {
        List<String> errors = new ArrayList<>();

        if (assignments == null || assignments.isEmpty()) {
            return TimetableValidationResult.failure("EMPTY_SCHEDULE", "No assignments generated to validate.");
        }

        Map<String, TimetableAssignment> sectionSlotMap = new HashMap<>();
        Map<String, TimetableAssignment> facultySlotMap = new HashMap<>();
        Map<String, TimetableAssignment> roomSlotMap = new HashMap<>();
        Map<Long, Integer> facultyLoadMap = new HashMap<>();

        for (TimetableAssignment a : assignments) {
            int duration = a.getDurationPeriods() != null ? a.getDurationPeriods() : 1;
            int start = a.getPeriodNumber();
            int end = start + duration - 1;

            // Basic checks
            if (start < 1 || end > 8) {
                errors.add(String.format("Invalid period range P%d-P%d for subject [%s].", start, end, a.getSubject().getSubjectCode()));
            }

            // Break checks
            if (start <= 2 && end >= 3) {
                errors.add(String.format("Class block for [%s] on %s crosses Morning Break.", a.getSubject().getSubjectCode(), a.getDayOfWeek()));
            }
            if (start <= 5 && end >= 6) {
                errors.add(String.format("Class block for [%s] on %s crosses Lunch Break.", a.getSubject().getSubjectCode(), a.getDayOfWeek()));
            }

            // Room capacity
            if (a.getRoom() != null && a.getRoom().getCapacity() != null && a.getBatch() != null && a.getBatch().getStrength() != null 
                    && a.getRoom().getCapacity() < a.getBatch().getStrength()) {
                errors.add(String.format("Room [%s] capacity (%d) insufficient for section [%s] (%d).",
                        a.getRoom().getRoomCode(), a.getRoom().getCapacity(), a.getBatch().getBatchName(), a.getBatch().getStrength()));
            }

            // Room type: Lab sessions must use LAB rooms
            boolean isLabSession = a.isLabBlock() || "LAB".equalsIgnoreCase(a.getActivityType()) || (a.getSubject() != null && "LAB".equalsIgnoreCase(a.getSubject().getSubjectType()));
            if (isLabSession && a.getRoom() != null && !isLabRoom(a.getRoom().getRoomType())) {
                errors.add(String.format("Lab session for [%s] requires LAB room, but room [%s] is %s.",
                        a.getSubject().getSubjectCode(), a.getRoom().getRoomCode(), a.getRoom().getRoomType()));
            }

            // Slot overlaps
            for (int p = start; p <= end; p++) {
                String slotKey = a.getDayOfWeek() + "-P" + p;

                if (a.getBatch() != null) {
                    String secKey = a.getBatch().getId() + "@" + slotKey;
                    if (sectionSlotMap.containsKey(secKey)) {
                        errors.add(String.format("Section [%s] conflict on %s at P%d.", a.getBatch().getBatchName(), a.getDayOfWeek(), p));
                    } else {
                        sectionSlotMap.put(secKey, a);
                    }
                }

                if (a.getFaculty() != null) {
                    String facKey = a.getFaculty().getId() + "@" + slotKey;
                    if (facultySlotMap.containsKey(facKey)) {
                        errors.add(String.format("Faculty [%s] conflict on %s at P%d.", a.getFaculty().getName(), a.getDayOfWeek(), p));
                    } else {
                        facultySlotMap.put(facKey, a);
                    }
                }

                if (a.getRoom() != null) {
                    String rmKey = a.getRoom().getId() + "@" + slotKey;
                    if (roomSlotMap.containsKey(rmKey)) {
                        errors.add(String.format("Room [%s] conflict on %s at P%d.", a.getRoom().getRoomCode(), a.getDayOfWeek(), p));
                    } else {
                        roomSlotMap.put(rmKey, a);
                    }
                }
            }

            // Faculty load (only academic and lab subjects count towards teaching workload)
            boolean isAcademic = a.getSubject() != null && !"SPECIAL".equalsIgnoreCase(a.getSubject().getSubjectType())
                    && ("ACADEMIC".equalsIgnoreCase(a.getActivityType()) || "LAB".equalsIgnoreCase(a.getActivityType()));
            if (isAcademic && a.getFaculty() != null) {
                int currentLoad = facultyLoadMap.getOrDefault(a.getFaculty().getId(), 0) + duration;
                facultyLoadMap.put(a.getFaculty().getId(), currentLoad);
                if (a.getFaculty().getMaxWeeklyLoad() != null && currentLoad > a.getFaculty().getMaxWeeklyLoad()) {
                    errors.add(String.format("Faculty [%s] workload (%d) exceeds max weekly limit (%d).",
                            a.getFaculty().getName(), currentLoad, a.getFaculty().getMaxWeeklyLoad()));
                }
            }
        }

        if (!errors.isEmpty()) {
            return TimetableValidationResult.failure("CONSTRAINT_VIOLATIONS", 
                    errors.size() + " constraint violation(s) detected.", errors);
        }

        return TimetableValidationResult.success("Entire schedule satisfies all hard constraints.");
    }

    /**
     * Determines if a room type is suitable for laboratory sessions.
     * Consistent with TimetableSolverService.isLabRoom().
     */
    private boolean isLabRoom(String roomType) {
        if (roomType == null || roomType.isBlank()) return false;
        String upper = roomType.toUpperCase().trim();
        return upper.equals("LAB") || upper.equals("LABORATORY") || upper.contains("_LAB") || upper.contains("LAB_");
    }
}
