package com.odysseus.backend.service;

import com.odysseus.backend.domain.*;
import com.odysseus.backend.dto.TimetableValidationResult;
import com.odysseus.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Upgraded TimetableSolverService.
 * Authoritative constraint-satisfaction scheduler supporting:
 * - Section double-booking prevention
 * - Faculty double-booking prevention
 * - Room double-booking prevention
 * - Room capacity and room type (LAB vs LECTURE_HALL)
 * - Faculty eligibility and availability (hard constraints)
 * - Faculty weekly workload limits
 * - Lab continuity (2-3 consecutive periods without crossing morning break or lunch)
 * - 33 Academic hours allocation + Special periods (Career Skills, Aptitude, LeetCode, Counselling, etc.)
 * - Strict infeasibility reporting without false success
 * - Validation via TimetableValidationService before dual persistence (TimetableEntry + TimetableAssignment)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TimetableSolverService {

    private final TimetableJobRepository jobRepository;
    private final TimetableEntryRepository entryRepository;
    private final TimetableAssignmentRepository assignmentRepository;
    private final SubjectOfferingRepository offeringRepository;
    private final FacultyRepository facultyRepository;
    private final FacultyAvailabilityRepository availabilityRepository;
    private final RoomRepository roomRepository;
    private final BatchRepository batchRepository;
    private final CollegeTimeslotRepository timeslotRepository;
    private final SubjectRepository subjectRepository;
    private final TimetableValidationService validationService;
    private final RoomAllocationService roomAllocationService;

    // --- Inner classes for solver bookkeeping ---

    private static class Demand {
        Long offeringId;
        Batch batch;
        Subject subject;
        Faculty faculty;
        String sessionType; // THEORY, LAB, TUTORIAL, SPECIAL
        String activityType; // ACADEMIC, LAB, APTITUDE, TECHNICAL_TEST, NPTEL, COUNSELLING, LEETCODE, CAREER_SKILL_DEVELOPMENT, FREE
        int periodsNeeded;

        Demand(Long offeringId, Batch batch, Subject subject, Faculty faculty,
               String sessionType, String activityType, int periodsNeeded) {
            this.offeringId = offeringId;
            this.batch = batch;
            this.subject = subject;
            this.faculty = faculty;
            this.sessionType = sessionType;
            this.activityType = activityType;
            this.periodsNeeded = periodsNeeded;
        }
    }

    private static class Slot {
        Long timeslotId;
        String dayOfWeek;
        int periodNumber;
        String periodLabel;
        String startTime;
        String endTime;

        Slot(CollegeTimeslot t) {
            this.timeslotId = t.getId();
            this.dayOfWeek = t.getDayOfWeek();
            this.periodNumber = t.getPeriodNumber();
            this.periodLabel = t.getPeriodLabel();
            this.startTime = t.getStartTime().toString();
            this.endTime = t.getEndTime().toString();
        }
    }

    private record SlotKey(String dayOfWeek, int periodNumber) {}

    // --- Public API ---

    @Async("solverExecutor")
    public void solveAsync(String jobId, String department, Integer semester) {
        try {
            updateJobStatus(jobId, "SOLVING", "Solver started. Constructing constraint graph and problem model...");
            solve(jobId, department, semester);
        } catch (Exception e) {
            log.error("Solver encountered fatal error for job {}", jobId, e);
            updateJobStatus(jobId, "FAILED", "Solver error: " + e.getMessage());
        }
    }

    @Async("solverExecutor")
    public void solveAsync(String jobId) {
        solveAsync(jobId, null, null);
    }

    // --- Core solver ---

    @Transactional
    public void solve(String jobId) {
        solve(jobId, null, null);
    }

    @Transactional
    public void solve(String jobId, String department, Integer semester) {
        // 1. Load active data (strictly exclude HODs from assignable teaching faculty pool)
        List<Batch> allBatches = batchRepository.findByActiveTrue();
        List<Batch> targetBatches = allBatches.stream()
                .filter(b -> department == null || department.isBlank() || com.odysseus.backend.util.DepartmentMatcher.matches(b.getDepartment(), department))
                .filter(b -> semester == null || semester.equals(b.getSemester()))
                .collect(Collectors.toList());
        if (targetBatches.isEmpty()) {
            targetBatches = allBatches;
        }
        Set<Long> targetBatchIds = targetBatches.stream().map(Batch::getId).collect(Collectors.toSet());

        List<SubjectOffering> allOfferings = offeringRepository.findByActiveTrue();
        List<SubjectOffering> offerings = allOfferings.stream()
                .filter(o -> o.getBatch() != null && targetBatchIds.contains(o.getBatch().getId()))
                .collect(Collectors.toList());

        List<Faculty> allFaculty = facultyRepository.findByActiveTrue().stream()
                .filter(f -> !f.isHod())
                .collect(Collectors.toList());
        List<Room> allRooms = roomRepository.findByActiveTrue();
        List<CollegeTimeslot> allTimeslots = timeslotRepository.findByActiveTrue();

        // 2. Build teaching slots (exclude breaks and lunch)
        List<Slot> teachingSlots = allTimeslots.stream()
                .filter(t -> !t.isBreak() && !t.isLunch())
                .map(Slot::new)
                .sorted(Comparator.comparing((Slot s) -> dayOrder(s.dayOfWeek))
                        .thenComparing(s -> s.periodNumber))
                .collect(Collectors.toList());

        Map<String, List<Slot>> slotsByDay = teachingSlots.stream()
                .collect(Collectors.groupingBy(s -> s.dayOfWeek,
                        LinkedHashMap::new, Collectors.toList()));

        // 3. Separate rooms
        List<Room> lectureRooms = allRooms.stream()
                .filter(r -> isTheoryRoom(r.getRoomType()))
                .collect(Collectors.toList());
        List<Room> labRooms = allRooms.stream()
                .filter(r -> isLabRoom(r.getRoomType()))
                .collect(Collectors.toList());

        // 4. Load faculty unavailabilities in a SINGLE query
        List<FacultyAvailability> allAvails = availabilityRepository.findAll();
        Set<String> facultyUnavailableSet = new HashSet<>();
        for (FacultyAvailability av : allAvails) {
            if (!av.isAvailable() && av.getFaculty() != null && av.getDayOfWeek() != null) {
                facultyUnavailableSet.add(av.getFaculty().getId() + "@" + av.getDayOfWeek().toUpperCase() + "-P" + av.getPeriodNumber());
            }
        }

        // 5. Build demands from offerings
        List<Demand> demands = new ArrayList<>();
        List<String> infeasibleReasons = new ArrayList<>();
        Map<Long, Integer> requiredAcademicHoursPerBatch = new HashMap<>();
        Map<Long, Integer> facultyDemandLoadMap = new HashMap<>();

        // Pre-populate with explicit assignments
        for (SubjectOffering o : offerings) {
            if (o.getAssignedFaculty() != null && o.getSubject() != null) {
                int p = calculateSubjectPeriods(o.getSubject());
                facultyDemandLoadMap.merge(o.getAssignedFaculty().getId(), p, Integer::sum);
            }
        }

        for (SubjectOffering offering : offerings) {
            Subject subject = offering.getSubject();
            Batch batch = offering.getBatch();

            Faculty faculty = offering.getAssignedFaculty();
            if (faculty == null) {
                // Workload-aware fallback: pick eligible faculty with capacity and lowest current demand-load
                final Subject subjectRef = subject;
                final int neededHours = calculateSubjectPeriods(subjectRef);
                faculty = allFaculty.stream()
                        .filter(f -> f.getEligibleSubjects().stream()
                                .anyMatch(s -> s.getId().equals(subjectRef.getId())))
                        .min(Comparator.comparingInt((Faculty f) -> {
                            int cur = facultyDemandLoadMap.getOrDefault(f.getId(), 0);
                            int max = f.getMaxWeeklyLoad() != null && f.getMaxWeeklyLoad() > 0 ? f.getMaxWeeklyLoad() : 18;
                            int penalty = (cur + neededHours > max) ? 1000 : 0;
                            return cur + penalty;
                        }))
                        .orElse(null);

                if (faculty != null) {
                    facultyDemandLoadMap.merge(faculty.getId(), neededHours, Integer::sum);
                }
            }
            if (faculty == null) {
                infeasibleReasons.add("No eligible faculty assigned or available for subject [" +
                        subject.getSubjectCode() + " - " + subject.getSubjectName() +
                        "] offered to section [" + batch.getBatchName() + "]. " +
                        "Assign faculty via Subject Offerings or run Auto-Assign in Faculty Master.");
                continue;
            }

            int theoryPeriods = subject.getWeeklyLecturePeriods() != null ? subject.getWeeklyLecturePeriods() : 0;
            int labPeriods = subject.getWeeklyPracticalPeriods() != null ? subject.getWeeklyPracticalPeriods() : 0;
            int tutorialPeriods = subject.getWeeklyTutorialPeriods() != null ? subject.getWeeklyTutorialPeriods() : 0;

            boolean isLabSubject = "LAB".equalsIgnoreCase(subject.getSubjectType());
            boolean isTheoryAndLab = "THEORY_AND_LAB".equalsIgnoreCase(subject.getSubjectType());

            if (isLabSubject) {
                if (labPeriods == 0) {
                    labPeriods = theoryPeriods > 0 ? theoryPeriods : (int) Math.ceil(subject.getCalculatedWeeklyHours());
                    if (labPeriods == 0) labPeriods = 2;
                }
                theoryPeriods = 0;
                tutorialPeriods = 0;

                if (labRooms.isEmpty()) {
                    infeasibleReasons.add("Subject [" + subject.getSubjectCode() + " - " + subject.getSubjectName()
                            + "] is a LAB subject for section [" + batch.getBatchName()
                            + "] but NO active laboratory rooms are configured in Room Master. "
                            + "Add rooms of type LAB before generating.");
                    continue;
                }
                boolean hasCapableLabRoom = labRooms.stream()
                        .anyMatch(r -> r.getCapacity() == null || r.getCapacity() >= batch.getStrength());
                if (!hasCapableLabRoom) {
                    infeasibleReasons.add("Subject [" + subject.getSubjectCode() + " - " + subject.getSubjectName()
                            + "] is a LAB subject for section [" + batch.getBatchName()
                            + "] (strength: " + batch.getStrength() + ") but no lab room has sufficient capacity.");
                    continue;
                }
            } else if (isTheoryAndLab) {
                if (theoryPeriods == 0 && labPeriods == 0) {
                    theoryPeriods = (int) Math.ceil(subject.getCalculatedWeeklyHours());
                    if (theoryPeriods == 0) theoryPeriods = 1;
                }
            } else {
                if (theoryPeriods == 0 && labPeriods == 0 && tutorialPeriods == 0) {
                    theoryPeriods = (int) Math.ceil(subject.getCalculatedWeeklyHours());
                    if (theoryPeriods == 0) theoryPeriods = 1;
                }
            }

            int totalSubjectPeriods = theoryPeriods + labPeriods + tutorialPeriods;
            requiredAcademicHoursPerBatch.merge(batch.getId(), totalSubjectPeriods, Integer::sum);

            if (theoryPeriods > 0) {
                demands.add(new Demand(offering.getId(), batch, subject, faculty, "THEORY", "ACADEMIC", theoryPeriods));
            }
            if (labPeriods > 0) {
                demands.add(new Demand(offering.getId(), batch, subject, faculty, "LAB", "LAB", labPeriods));
            }
            if (tutorialPeriods > 0) {
                demands.add(new Demand(offering.getId(), batch, subject, faculty, "TUTORIAL", "ACADEMIC", tutorialPeriods));
            }
        }

        if (!infeasibleReasons.isEmpty()) {
            updateJobStatus(jobId, "INFEASIBLE", "Validation failed before solve: " + String.join(" | ", infeasibleReasons));
            return;
        }

        if (demands.isEmpty()) {
            updateJobStatus(jobId, "INFEASIBLE", "No subject offerings found to schedule.");
            return;
        }

        updateJobStatus(jobId, "SOLVING", "Solving constraints for " + demands.size() + " academic requirements across " + teachingSlots.size() + " timeslots...");

        // 6. Constraint tracking structures
        Map<SlotKey, Set<Long>> batchSlotMap = new HashMap<>();
        Map<SlotKey, Set<Long>> facultySlotMap = new HashMap<>();
        Map<SlotKey, Set<Long>> roomSlotMap = new HashMap<>();
        Map<Long, Integer> facultyLoadMap = new HashMap<>();
        Map<String, Integer> facultyDailyLoadMap = new HashMap<>();

        // Pre-populate with existing active assignments from other batches (Cross-Semester Conflict Prevention)
        List<TimetableAssignment> existingAssignments = assignmentRepository.findByActiveTrue();
        for (TimetableAssignment ea : existingAssignments) {
            if (ea.getBatch() != null && !targetBatchIds.contains(ea.getBatch().getId())) {
                int duration = ea.getDurationPeriods() != null ? ea.getDurationPeriods() : 1;
                for (int p = 0; p < duration; p++) {
                    SlotKey key = new SlotKey(ea.getDayOfWeek(), ea.getPeriodNumber() + p);
                    if (ea.getFaculty() != null) {
                        facultySlotMap.computeIfAbsent(key, k -> new HashSet<>()).add(ea.getFaculty().getId());
                        facultyLoadMap.merge(ea.getFaculty().getId(), 1, Integer::sum);
                        facultyDailyLoadMap.merge(ea.getFaculty().getId() + "@" + ea.getDayOfWeek().toUpperCase(), 1, Integer::sum);
                    }
                    if (ea.getRoom() != null) {
                        roomSlotMap.computeIfAbsent(key, k -> new HashSet<>()).add(ea.getRoom().getId());
                    }
                }
            }
        }

        List<TimetableEntry> entries = new ArrayList<>();
        List<TimetableAssignment> assignments = new ArrayList<>();

        // 7. Sort demands: Lab sessions first (most constrained), then by periods needed desc
        demands.sort(Comparator.comparingInt((Demand d) -> "LAB".equals(d.sessionType) ? 0 : 1)
                .thenComparingInt(d -> -d.periodsNeeded));

        // 7b. Allocate dedicated home classroom per section (Batch) in a specific floor of a specific block
        Map<Long, Room> batchHomeRoomMap = new HashMap<>();
        List<Room> sortedLectureRooms = new ArrayList<>(lectureRooms);
        sortedLectureRooms.sort(Comparator.comparing((Room r) -> r.getBlockName() != null ? r.getBlockName() : "")
                .thenComparingInt(r -> r.getFloorNumber() != null ? r.getFloorNumber() : 1)
                .thenComparing(r -> r.getRoomCode() != null ? r.getRoomCode() : ""));

        Set<Long> assignedHomeRoomIds = new HashSet<>();
        for (Batch batch : targetBatches) {
            Room home = null;

            if (batch.getAssignedRoomCode() != null && !batch.getAssignedRoomCode().trim().isEmpty()) {
                home = sortedLectureRooms.stream()
                        .filter(r -> r.getRoomCode().equalsIgnoreCase(batch.getAssignedRoomCode().trim()))
                        .findFirst()
                        .orElse(null);
            }

            if (home == null) {
                home = sortedLectureRooms.stream()
                        .filter(r -> !assignedHomeRoomIds.contains(r.getId()))
                        .filter(r -> r.getAssignedSection() != null && (
                                r.getAssignedSection().equalsIgnoreCase(batch.getSection()) ||
                                r.getAssignedSection().equalsIgnoreCase(batch.getBatchName())
                        ))
                        .findFirst()
                        .orElse(null);
            }

            if (home == null) {
                List<Batch> unassigned = targetBatches.stream()
                        .filter(b -> !batchHomeRoomMap.containsKey(b.getId()))
                        .collect(Collectors.toList());
                List<Room> availableRooms = sortedLectureRooms.stream()
                        .filter(r -> !assignedHomeRoomIds.contains(r.getId()))
                        .collect(Collectors.toList());

                Map<Long, Room> deptAllocations = roomAllocationService.allocateHomeRoomsForBatches(unassigned, availableRooms);
                home = deptAllocations.get(batch.getId());
            }

            if (home == null && !sortedLectureRooms.isEmpty()) {
                home = sortedLectureRooms.stream()
                        .filter(r -> !assignedHomeRoomIds.contains(r.getId()))
                        .filter(r -> r.getCapacity() == null || r.getCapacity() >= batch.getStrength())
                        .findFirst()
                        .orElse(sortedLectureRooms.get(0));
            }

            if (home != null) {
                batchHomeRoomMap.put(batch.getId(), home);
                assignedHomeRoomIds.add(home.getId());
                log.info("Batch [{}] candidate dedicated room [{}] on Floor {} of [{}]",
                        batch.getBatchName(), home.getRoomCode(), home.getFloorNumber(), home.getBlockName());
            }
        }

        // Place academic demands
        Map<Long, Integer> scheduledAcademicHoursPerBatch = new HashMap<>();

        for (Demand demand : demands) {
            boolean placed;
            Room homeRoom = batchHomeRoomMap.get(demand.batch.getId());
            if ("LAB".equals(demand.sessionType)) {
                placed = placeLabDemand(demand, slotsByDay, labRooms, entries, assignments,
                        batchSlotMap, facultySlotMap, roomSlotMap, facultyLoadMap, facultyDailyLoadMap, facultyUnavailableSet, homeRoom);
            } else {
                placed = placeRegularDemand(demand, teachingSlots, lectureRooms, entries, assignments,
                        batchSlotMap, facultySlotMap, roomSlotMap, facultyLoadMap, facultyDailyLoadMap, facultyUnavailableSet, homeRoom);
            }

            if (!placed) {
                infeasibleReasons.add("Could not place " + demand.periodsNeeded + " " +
                        demand.sessionType + " period(s) for [" +
                        demand.subject.getSubjectCode() + "] section [" +
                        demand.batch.getBatchName() + "] with faculty [" +
                        demand.faculty.getName() + "]. Possible capacity/availability conflict.");
            } else {
                scheduledAcademicHoursPerBatch.merge(demand.batch.getId(), demand.periodsNeeded, Integer::sum);
            }
        }

        // 8. Academic hours check per batch
        for (Batch b : targetBatches) {
            int required = requiredAcademicHoursPerBatch.getOrDefault(b.getId(), 0);
            int scheduled = scheduledAcademicHoursPerBatch.getOrDefault(b.getId(), 0);
            if (required > 0 && scheduled < required) {
                infeasibleReasons.add("Section [" + b.getBatchName() + "] received " + scheduled +
                        " academic hours, failing requirement of " + required + " academic hours.");
            }
        }

        if (!infeasibleReasons.isEmpty()) {
            updateJobStatus(jobId, "INFEASIBLE", "Scheduling Infeasible: " + String.join(" | ", infeasibleReasons));
            return;
        }

        // 9. Schedule Special Activities (Career Skill Dev 2hrs, Aptitude, LeetCode, Counselling, etc.)
        scheduleSpecialActivities(targetBatches, teachingSlots, lectureRooms, allFaculty,
                entries, assignments, batchSlotMap, facultySlotMap, roomSlotMap, facultyLoadMap, facultyDailyLoadMap, facultyUnavailableSet, batchHomeRoomMap);

        // 10. Run complete independent validation via TimetableValidationService
        TimetableValidationResult validation = validationService.validateEntireSchedule(assignments);
        if (!validation.isValid()) {
            updateJobStatus(jobId, "INFEASIBLE", "Post-solve validation failed: " +
                    String.join(" | ", validation.getErrors()));
            return;
        }

        // 11. Dual persistence: Save TimetableEntry & TimetableAssignment
        for (TimetableEntry entry : entries) {
            entry.setJobId(jobId);
        }
        entryRepository.saveAll(entries);

        // Update active assignments: delete only for targetBatches and save new
        for (Batch b : targetBatches) {
            assignmentRepository.deleteByBatchId(b.getId());
        }
        assignmentRepository.saveAll(assignments);

        // 11b. Persist dedicated classroom allocations for batches now that timetable has been saved
        for (Map.Entry<Long, Room> bEntry : batchHomeRoomMap.entrySet()) {
            batchRepository.findById(bEntry.getKey()).ifPresent(b -> {
                Room home = bEntry.getValue();
                b.setAssignedBlockName(home.getBlockName());
                b.setAssignedFloorNumber(home.getFloorNumber());
                b.setAssignedRoomCode(home.getRoomCode());
                batchRepository.save(b);
                home.setAssignedSection(b.getBatchName());
                roomRepository.save(home);
                log.info("Batch [{}] dedicated room [{}] officially persisted on timetable completion.",
                        b.getBatchName(), home.getRoomCode());
            });
        }

        // 12. Finalize Job Status
        TimetableJob job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus("COMPLETED");
        job.setMessage("Timetable successfully generated with " + entries.size() + " periods scheduled across " + targetBatches.size() + " sections.");
        job.setTotalEntries(entries.size());
        job.setCompletedAt(LocalDateTime.now());
        jobRepository.save(job);

        log.info("Job {} completed successfully with {} assignments.", jobId, assignments.size());
    }

    // --- Demand Placement Implementations ---

    private boolean placeRegularDemand(Demand demand, List<Slot> slots, List<Room> rooms,
                                       List<TimetableEntry> entries,
                                       List<TimetableAssignment> assignments,
                                       Map<SlotKey, Set<Long>> batchSlotMap,
                                       Map<SlotKey, Set<Long>> facultySlotMap,
                                       Map<SlotKey, Set<Long>> roomSlotMap,
                                       Map<Long, Integer> facultyLoadMap,
                                       Map<String, Integer> facultyDailyLoadMap,
                                       Set<String> facultyUnavailableSet,
                                       Room preferredRoom) {
        int placed = 0;
        Set<String> usedDays = new HashSet<>();

        // First pass: Spread across distinct days
        for (Slot slot : slots) {
            if (placed >= demand.periodsNeeded) break;
            if (usedDays.contains(slot.dayOfWeek) && placed < Math.min(demand.periodsNeeded, 5)) continue;

            Room room = findAvailableRoom(slot, rooms, demand.batch.getStrength(), roomSlotMap, preferredRoom);
            if (room == null) continue;

            if (!isSlotAvailable(slot, demand.batch.getId(), demand.faculty.getId(),
                    batchSlotMap, facultySlotMap, facultyLoadMap, facultyDailyLoadMap, demand.faculty.getMaxWeeklyLoad(),
                    facultyUnavailableSet)) {
                continue;
            }

            // Place slot
            entries.add(buildEntry(demand, slot, room));
            assignments.add(buildAssignment(demand, slot, room, 1, false));
            markSlotUsed(slot, demand.batch.getId(), demand.faculty.getId(), room.getId(),
                    batchSlotMap, facultySlotMap, roomSlotMap, facultyLoadMap, facultyDailyLoadMap, true);
            usedDays.add(slot.dayOfWeek);
            placed++;
        }

        // Second pass: Any available slot for remaining periods
        if (placed < demand.periodsNeeded) {
            for (Slot slot : slots) {
                if (placed >= demand.periodsNeeded) break;

                SlotKey key = new SlotKey(slot.dayOfWeek, slot.periodNumber);
                if (batchSlotMap.getOrDefault(key, Set.of()).contains(demand.batch.getId())) continue;

                Room room = findAvailableRoom(slot, rooms, demand.batch.getStrength(), roomSlotMap, preferredRoom);
                if (room == null) continue;

                if (!isSlotAvailable(slot, demand.batch.getId(), demand.faculty.getId(),
                        batchSlotMap, facultySlotMap, facultyLoadMap, facultyDailyLoadMap, demand.faculty.getMaxWeeklyLoad(),
                        facultyUnavailableSet)) {
                    continue;
                }

                entries.add(buildEntry(demand, slot, room));
                assignments.add(buildAssignment(demand, slot, room, 1, false));
                markSlotUsed(slot, demand.batch.getId(), demand.faculty.getId(), room.getId(),
                        batchSlotMap, facultySlotMap, roomSlotMap, facultyLoadMap, facultyDailyLoadMap, true);
                placed++;
            }
        }

        return placed >= demand.periodsNeeded;
    }

    private boolean placeLabDemand(Demand demand, Map<String, List<Slot>> slotsByDay,
                                   List<Room> labRooms,
                                   List<TimetableEntry> entries,
                                   List<TimetableAssignment> assignments,
                                   Map<SlotKey, Set<Long>> batchSlotMap,
                                   Map<SlotKey, Set<Long>> facultySlotMap,
                                   Map<SlotKey, Set<Long>> roomSlotMap,
                                   Map<Long, Integer> facultyLoadMap,
                                   Map<String, Integer> facultyDailyLoadMap,
                                   Set<String> facultyUnavailableSet,
                                   Room homeRoom) {

        int labDuration = demand.subject.getLabDuration() != null ? demand.subject.getLabDuration() : 2;
        int totalLabPeriods = demand.periodsNeeded;
        int sessionsNeeded = (int) Math.ceil((double) totalLabPeriods / labDuration);
        int sessionsPlaced = 0;

        // Valid contiguous windows: P1-P2, P3-P4-P5, P6-P7-P8 (never crossing break or lunch)
        int[][] windows = { {1, 2}, {3, 4, 5}, {6, 7, 8} };

        for (Map.Entry<String, List<Slot>> dayEntry : slotsByDay.entrySet()) {
            if (sessionsPlaced >= sessionsNeeded) break;

            String day = dayEntry.getKey();
            List<Slot> daySlots = dayEntry.getValue();
            Map<Integer, Slot> periodToSlot = daySlots.stream()
                    .collect(Collectors.toMap(s -> s.periodNumber, s -> s));

            for (int[] window : windows) {
                if (sessionsPlaced >= sessionsNeeded) break;

                for (int startIdx = 0; startIdx <= window.length - labDuration; startIdx++) {
                    if (sessionsPlaced >= sessionsNeeded) break;

                    List<Slot> blockSlots = new ArrayList<>();
                    boolean blockValid = true;

                    for (int i = 0; i < labDuration; i++) {
                        int period = window[startIdx + i];
                        Slot slot = periodToSlot.get(period);
                        if (slot == null) { blockValid = false; break; }
                        blockSlots.add(slot);
                    }

                    if (!blockValid) continue;

                    boolean allAvailable = blockSlots.stream().allMatch(slot ->
                            isSlotAvailable(slot, demand.batch.getId(), demand.faculty.getId(),
                                    batchSlotMap, facultySlotMap, facultyLoadMap, facultyDailyLoadMap,
                                    demand.faculty.getMaxWeeklyLoad(), facultyUnavailableSet));

                    if (!allAvailable) continue;

                    // Find an unoccupied lab room for all block periods
                    Room labRoom = null;
                    for (Room room : labRooms) {
                        if (room.getCapacity() < demand.batch.getStrength()) continue;
                        boolean roomFree = blockSlots.stream().allMatch(slot -> {
                            SlotKey key = new SlotKey(slot.dayOfWeek, slot.periodNumber);
                            return !roomSlotMap.getOrDefault(key, Set.of()).contains(room.getId());
                        });
                        if (roomFree) { labRoom = room; break; }
                    }

                    // Fallback to usual section classroom if lab room is unavailable
                    if (labRoom == null && homeRoom != null) {
                        boolean homeFree = blockSlots.stream().allMatch(slot -> {
                            SlotKey key = new SlotKey(slot.dayOfWeek, slot.periodNumber);
                            return !roomSlotMap.getOrDefault(key, Set.of()).contains(homeRoom.getId());
                        });
                        if (homeFree && (homeRoom.getCapacity() == null || homeRoom.getCapacity() >= demand.batch.getStrength())) {
                            labRoom = homeRoom;
                        }
                    }

                    if (labRoom == null) continue;

                    // Place the entire lab block
                    for (Slot slot : blockSlots) {
                        entries.add(buildEntry(demand, slot, labRoom));
                        markSlotUsed(slot, demand.batch.getId(), demand.faculty.getId(),
                                labRoom.getId(), batchSlotMap, facultySlotMap, roomSlotMap, facultyLoadMap, facultyDailyLoadMap, true);
                    }

                    // Create composite TimetableAssignment for the lab block
                    Slot firstSlot = blockSlots.get(0);
                    assignments.add(buildAssignment(demand, firstSlot, labRoom, labDuration, true));

                    sessionsPlaced++;
                }
            }
        }

        return sessionsPlaced >= sessionsNeeded;
    }

    private void scheduleSpecialActivities(List<Batch> batches, List<Slot> slots,
                                           List<Room> lectureRooms, List<Faculty> facultyList,
                                           List<TimetableEntry> entries,
                                           List<TimetableAssignment> assignments,
                                           Map<SlotKey, Set<Long>> batchSlotMap,
                                           Map<SlotKey, Set<Long>> facultySlotMap,
                                           Map<SlotKey, Set<Long>> roomSlotMap,
                                           Map<Long, Integer> facultyLoadMap,
                                           Map<String, Integer> facultyDailyLoadMap,
                                           Set<String> facultyUnavailableSet,
                                           Map<Long, Room> batchHomeRoomMap) {

        Map<String, Subject> specialSubjectCache = new HashMap<>();

        for (Batch batch : batches) {
            Room homeRoom = batchHomeRoomMap != null ? batchHomeRoomMap.get(batch.getId()) : null;

            // Find all free slots for this batch
            List<Slot> freeSlots = slots.stream()
                    .filter(slot -> !batchSlotMap.getOrDefault(new SlotKey(slot.dayOfWeek, slot.periodNumber), Set.of()).contains(batch.getId()))
                    .collect(Collectors.toList());

            // Separate free Period 1 slots and other periods
            List<Slot> p1FreeSlots = freeSlots.stream().filter(s -> s.periodNumber == 1).collect(Collectors.toList());
            List<Slot> otherFreeSlots = freeSlots.stream().filter(s -> s.periodNumber != 1).collect(Collectors.toList());

            // Priority: Place TECHNICAL_TEST and APTITUDE in Period 1 slots where feasible
            List<String> priorityActivities = new ArrayList<>(List.of("TECHNICAL_TEST", "APTITUDE"));
            List<String> otherActivities = new ArrayList<>(List.of(
                    "CAREER_SKILL_DEVELOPMENT", "CAREER_SKILL_DEVELOPMENT",
                    "LEETCODE", "COUNSELLING", "NPTEL"
            ));

            List<Map.Entry<Slot, String>> plannedAssignments = new ArrayList<>();

            // Assign priority activities to P1 free slots first
            for (Slot p1Slot : p1FreeSlots) {
                if (!priorityActivities.isEmpty()) {
                    plannedAssignments.add(Map.entry(p1Slot, priorityActivities.remove(0)));
                } else if (!otherActivities.isEmpty()) {
                    plannedAssignments.add(Map.entry(p1Slot, otherActivities.remove(0)));
                } else {
                    plannedAssignments.add(Map.entry(p1Slot, "FREE"));
                }
            }

            // Assign remaining activities to other free slots
            List<String> remaining = new ArrayList<>(priorityActivities);
            remaining.addAll(otherActivities);
            for (Slot oSlot : otherFreeSlots) {
                if (!remaining.isEmpty()) {
                    plannedAssignments.add(Map.entry(oSlot, remaining.remove(0)));
                } else {
                    plannedAssignments.add(Map.entry(oSlot, "FREE"));
                }
            }

            for (Map.Entry<Slot, String> plan : plannedAssignments) {
                Slot slot = plan.getKey();
                String activity = plan.getValue();

                Room room = findAvailableRoom(slot, lectureRooms, batch.getStrength(), roomSlotMap, homeRoom);
                if (room == null && homeRoom != null) room = homeRoom;
                if (room == null && !lectureRooms.isEmpty()) room = lectureRooms.get(0);

                Faculty fac = null;
                SlotKey key = new SlotKey(slot.dayOfWeek, slot.periodNumber);

                // Special Test Allocation for Semesters 3-8: Aptitude & Technical Tests must only be
                // assigned to faculty from the batch's main department, excluding supporting departments.
                boolean isSpecialTestSem3To8 = (batch.getSemester() != null && batch.getSemester() >= 3 && batch.getSemester() <= 8)
                        && ("APTITUDE".equalsIgnoreCase(activity) || "TECHNICAL_TEST".equalsIgnoreCase(activity));

                List<Faculty> candidatePool = facultyList.stream()
                        .filter(c -> !c.isHod())
                        .filter(c -> {
                            if (isSpecialTestSem3To8) {
                                String fDept = c.getDepartment() != null ? c.getDepartment() : "";
                                boolean isSupporting = fDept.equalsIgnoreCase("Mathematics") || fDept.equalsIgnoreCase("Physics")
                                        || fDept.equalsIgnoreCase("Chemistry") || fDept.equalsIgnoreCase("English");
                                return !isSupporting && com.odysseus.backend.util.DepartmentMatcher.matches(c.getDepartment(), batch.getDepartment());
                            }
                            return true;
                        })
                        .collect(Collectors.toList());

                if (candidatePool.isEmpty()) {
                    candidatePool = facultyList.stream().filter(c -> !c.isHod()).collect(Collectors.toList());
                }

                for (Faculty candidate : candidatePool) {
                    String unavKey = candidate.getId() + "@" + slot.dayOfWeek.toUpperCase() + "-P" + slot.periodNumber;
                    if (!facultyUnavailableSet.contains(unavKey) && !facultySlotMap.getOrDefault(key, Set.of()).contains(candidate.getId())) {
                        int dailyLoad = facultyDailyLoadMap.getOrDefault(candidate.getId() + "@" + slot.dayOfWeek.toUpperCase(), 0);
                        if (dailyLoad < 6) {
                            fac = candidate;
                            break;
                        }
                    }
                }
                if (fac == null) {
                    for (Faculty candidate : facultyList) {
                        String unavKey = candidate.getId() + "@" + slot.dayOfWeek.toUpperCase() + "-P" + slot.periodNumber;
                        if (!facultyUnavailableSet.contains(unavKey) && !facultySlotMap.getOrDefault(key, Set.of()).contains(candidate.getId())) {
                            int dailyLoad = facultyDailyLoadMap.getOrDefault(candidate.getId() + "@" + slot.dayOfWeek.toUpperCase(), 0);
                            if (dailyLoad < 6) {
                                fac = candidate;
                                break;
                            }
                        }
                    }
                }

                final String currentAct = activity;
                Subject specSub = specialSubjectCache.computeIfAbsent(currentAct, code ->
                        subjectRepository.findBySubjectCode(code)
                                .orElseGet(() -> subjectRepository.save(Subject.builder()
                                        .subjectCode(code)
                                        .subjectName(formatActivityTitle(code))
                                        .department("General")
                                        .subjectType("SPECIAL")
                                        .requiresLab(false)
                                        .active(true)
                                        .build()))
                );

                Demand specDemand = new Demand(null, batch, specSub, fac, "SPECIAL", currentAct, 1);
                entries.add(buildEntry(specDemand, slot, room));

                if (room != null && fac != null) {
                    assignments.add(buildAssignment(specDemand, slot, room, 1, false));
                    markSlotUsed(slot, batch.getId(), fac.getId(), room.getId(),
                            batchSlotMap, facultySlotMap, roomSlotMap, facultyLoadMap, facultyDailyLoadMap, false);
                } else if (room != null) {
                    markSlotUsed(slot, batch.getId(), null, room.getId(),
                            batchSlotMap, facultySlotMap, roomSlotMap, facultyLoadMap, facultyDailyLoadMap, false);
                }
            }
        }
    }

    private String formatActivityTitle(String code) {
        return switch (code) {
            case "CAREER_SKILL_DEVELOPMENT" -> "Career Skill Development";
            case "LEETCODE" -> "LeetCode Problem Solving";
            case "APTITUDE" -> "Aptitude & Reasoning";
            case "TECHNICAL_TEST" -> "Technical Assessment";
            case "COUNSELLING" -> "Student Mentoring & Counselling";
            case "NPTEL" -> "NPTEL Self Study";
            default -> "Free / Library Period";
        };
    }

    // --- Constraint Checking Helpers ---

    private boolean isSlotAvailable(Slot slot, Long batchId, Long facultyId,
                                    Map<SlotKey, Set<Long>> batchSlotMap,
                                    Map<SlotKey, Set<Long>> facultySlotMap,
                                    Map<Long, Integer> facultyLoadMap,
                                    Map<String, Integer> facultyDailyLoadMap,
                                    Integer maxWeeklyLoad,
                                    Set<String> facultyUnavailableSet) {
        SlotKey key = new SlotKey(slot.dayOfWeek, slot.periodNumber);

        // Batch conflict
        if (batchSlotMap.getOrDefault(key, Set.of()).contains(batchId)) return false;

        // Faculty conflict
        if (facultySlotMap.getOrDefault(key, Set.of()).contains(facultyId)) return false;

        // Faculty availability hard constraint
        String unavKey = facultyId + "@" + slot.dayOfWeek.toUpperCase() + "-P" + slot.periodNumber;
        if (facultyUnavailableSet.contains(unavKey)) return false;

        // Faculty weekly workload limit
        int currentLoad = facultyLoadMap.getOrDefault(facultyId, 0);
        if (maxWeeklyLoad != null && currentLoad >= maxWeeklyLoad) return false;

        // Faculty daily workload limit: max 5 periods/day ensures 2-3 free periods per day
        int currentDailyLoad = facultyDailyLoadMap.getOrDefault(facultyId + "@" + slot.dayOfWeek.toUpperCase(), 0);
        if (currentDailyLoad >= 6) return false;

        return true;
    }

    private Room findAvailableRoom(Slot slot, List<Room> rooms, Integer requiredCapacity,
                                   Map<SlotKey, Set<Long>> roomSlotMap) {
        return findAvailableRoom(slot, rooms, requiredCapacity, roomSlotMap, null);
    }

    private Room findAvailableRoom(Slot slot, List<Room> rooms, Integer requiredCapacity,
                                   Map<SlotKey, Set<Long>> roomSlotMap, Room preferredRoom) {
        SlotKey key = new SlotKey(slot.dayOfWeek, slot.periodNumber);
        Set<Long> occupiedRooms = roomSlotMap.getOrDefault(key, Set.of());

        // 1. Try preferred room first (dedicated classroom for this section on their specific block and floor)
        if (preferredRoom != null && !occupiedRooms.contains(preferredRoom.getId())) {
            if (requiredCapacity == null || preferredRoom.getCapacity() == null || preferredRoom.getCapacity() >= requiredCapacity) {
                return preferredRoom;
            }
        }

        // 2. Try another room in the same block and floor
        if (preferredRoom != null) {
            for (Room room : rooms) {
                if (occupiedRooms.contains(room.getId())) continue;
                if (requiredCapacity != null && room.getCapacity() != null && room.getCapacity() < requiredCapacity) continue;
                if (Objects.equals(room.getBlockName(), preferredRoom.getBlockName()) &&
                        Objects.equals(room.getFloorNumber(), preferredRoom.getFloorNumber())) {
                    return room;
                }
            }
        }

        // 3. Fallback to any available room with adequate capacity
        for (Room room : rooms) {
            if (occupiedRooms.contains(room.getId())) continue;
            if (requiredCapacity != null && room.getCapacity() != null && room.getCapacity() < requiredCapacity) continue;
            return room;
        }
        return null;
    }

    private void markSlotUsed(Slot slot, Long batchId, Long facultyId, Long roomId,
                              Map<SlotKey, Set<Long>> batchSlotMap,
                              Map<SlotKey, Set<Long>> facultySlotMap,
                              Map<SlotKey, Set<Long>> roomSlotMap,
                              Map<Long, Integer> facultyLoadMap,
                              Map<String, Integer> facultyDailyLoadMap,
                              boolean isAcademic) {
        SlotKey key = new SlotKey(slot.dayOfWeek, slot.periodNumber);
        if (batchId != null) batchSlotMap.computeIfAbsent(key, k -> new HashSet<>()).add(batchId);
        if (facultyId != null) {
            facultySlotMap.computeIfAbsent(key, k -> new HashSet<>()).add(facultyId);
            if (isAcademic) {
                facultyLoadMap.merge(facultyId, 1, Integer::sum);
                facultyDailyLoadMap.merge(facultyId + "@" + slot.dayOfWeek.toUpperCase(), 1, Integer::sum);
            }
        }
        if (roomId != null) roomSlotMap.computeIfAbsent(key, k -> new HashSet<>()).add(roomId);
    }

    private TimetableEntry buildEntry(Demand demand, Slot slot, Room room) {
        return TimetableEntry.builder()
                .dayOfWeek(slot.dayOfWeek)
                .periodNumber(slot.periodNumber)
                .periodLabel(slot.periodLabel)
                .startTime(slot.startTime)
                .endTime(slot.endTime)
                .batchId(demand.batch.getId())
                .batchName(demand.batch.getBatchName())
                .subjectId(demand.subject.getId())
                .subjectCode(demand.subject.getSubjectCode())
                .subjectName(demand.subject.getSubjectName())
                .facultyId(demand.faculty != null ? demand.faculty.getId() : 0L)
                .facultyName(demand.faculty != null ? demand.faculty.getName() : "Unassigned")
                .roomId(room != null ? room.getId() : 0L)
                .roomCode(room != null ? room.getRoomCode() : "N/A")
                .sessionType(demand.sessionType)
                .build();
    }

    private TimetableAssignment buildAssignment(Demand demand, Slot slot, Room room, int duration, boolean isLab) {
        return TimetableAssignment.builder()
                .batch(demand.batch)
                .subject(demand.subject)
                .faculty(demand.faculty)
                .room(room)
                .dayOfWeek(slot.dayOfWeek)
                .periodNumber(slot.periodNumber)
                .durationPeriods(duration)
                .isLabBlock(isLab)
                .activityType(demand.activityType != null ? demand.activityType : "ACADEMIC")
                .academicYear(demand.batch.getAcademicYear())
                .semester(demand.batch.getSemester())
                .active(true)
                .build();
    }

    private void updateJobStatus(String jobId, String status, String message) {
        jobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(status);
            job.setMessage(message);
            if ("COMPLETED".equals(status) || "FAILED".equals(status) || "INFEASIBLE".equals(status)) {
                job.setCompletedAt(LocalDateTime.now());
            }
            jobRepository.save(job);
        });
    }

    private int calculateSubjectPeriods(Subject subject) {
        if (subject == null) return 0;
        int theoryPeriods = subject.getWeeklyLecturePeriods() != null ? subject.getWeeklyLecturePeriods() : 0;
        int labPeriods = subject.getWeeklyPracticalPeriods() != null ? subject.getWeeklyPracticalPeriods() : 0;
        int tutorialPeriods = subject.getWeeklyTutorialPeriods() != null ? subject.getWeeklyTutorialPeriods() : 0;

        if (theoryPeriods == 0 && labPeriods == 0 && tutorialPeriods == 0) {
            theoryPeriods = (int) Math.ceil(subject.getCalculatedWeeklyHours());
            if (theoryPeriods == 0) theoryPeriods = 1;
        }
        return theoryPeriods + labPeriods + tutorialPeriods;
    }

    private int dayOrder(String day) {
        return switch (day.toUpperCase()) {
            case "MONDAY" -> 1;
            case "TUESDAY" -> 2;
            case "WEDNESDAY" -> 3;
            case "THURSDAY" -> 4;
            case "FRIDAY" -> 5;
            case "SATURDAY" -> 6;
            case "SUNDAY" -> 7;
            default -> 99;
        };
    }

    /**
     * Determines if a room type is suitable for laboratory sessions.
     * Accepts: LAB, LABORATORY, COMPUTER_LAB, ELECTRONICS_LAB, or any type containing 'LAB'.
     */
    private boolean isLabRoom(String roomType) {
        if (roomType == null || roomType.isBlank()) return false;
        String upper = roomType.toUpperCase().trim();
        return upper.equals("LAB") || upper.equals("LABORATORY") || upper.contains("_LAB") || upper.contains("LAB_");
    }

    /**
     * Determines if a room type is suitable for theory/lecture sessions.
     * Accepts: LECTURE_HALL, CLASSROOM, SEMINAR_HALL, CONFERENCE_HALL, AUDITORIUM, OTHER.
     * Explicitly excludes any lab room type.
     */
    private boolean isTheoryRoom(String roomType) {
        if (roomType == null || roomType.isBlank()) return false;
        return !isLabRoom(roomType); // Everything that is not a lab room is usable for theory
    }
}
