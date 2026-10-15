package com.odysseus.backend.service;

import com.odysseus.backend.domain.Batch;
import com.odysseus.backend.domain.Room;
import com.odysseus.backend.repository.BatchRepository;
import com.odysseus.backend.repository.RoomRepository;
import com.odysseus.backend.util.DepartmentMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Intelligent Department-Wise Floor & Room Allocation Service.
 *
 * Implements the institutional rule:
 * ONE DEPARTMENT → ONE PREFERRED FLOOR / ALLOCATED FLOOR RANGE
 * Rooms on that floor are automatically distributed according to the number of sections.
 *
 * CORE PRINCIPLES:
 * 1. Room Master is the single source of truth for physical infrastructure.
 * 2. Department-floor mapping is derived dynamically from Room Master configuration (no hardcoding).
 * 3. Number of active sections drives room requirements.
 * 4. Room capacity must be validated against section student strength.
 * 5. Theory subjects use the dedicated section classroom; specialized labs are handled separately.
 * 6. Capacity-aware overflow: if preferred floor has fewer rooms than sections, uses secondary floor and reports clearly.
 * 7. Room allocation happens before timetable generation, and can be reviewed and confirmed by admin.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoomAllocationService {

    private final RoomRepository roomRepository;
    private final BatchRepository batchRepository;

    public record SectionRoomAllocation(
            Long batchId,
            String batchName,
            String section,
            Integer semester,
            Integer strength,
            Long roomId,
            String roomCode,
            String roomName,
            String blockName,
            Integer floorNumber,
            Integer roomCapacity,
            boolean capacityValid,
            boolean isPreferredFloor,
            String statusMessage
    ) {}

    public record DepartmentRoomProposal(
            String department,
            int totalSections,
            String preferredBlock,
            Integer preferredFloor,
            Integer secondaryFloor,
            int roomsAvailableOnPreferredFloor,
            int allocatedCount,
            boolean allAllocated,
            boolean capacitySatisfied,
            boolean floorPreferenceSatisfied,
            List<SectionRoomAllocation> allocations,
            List<String> notices,
            List<String> warnings
    ) {}

    public record FullRoomAllocationResult(
            List<DepartmentRoomProposal> departmentProposals,
            int totalSections,
            int totalAllocated,
            List<String> globalWarnings
    ) {}

    /**
     * Generate room allocation proposals for all or a specific department.
     */
    public FullRoomAllocationResult generateProposals(String departmentFilter) {
        List<Batch> allBatches = batchRepository.findByActiveTrue();
        List<Room> allRooms = roomRepository.findByActiveTrue();

        // Only lecture/classroom rooms are allocated as section home rooms (labs are specialized resources)
        List<Room> lectureRooms = allRooms.stream()
                .filter(r -> !isLabRoom(r.getRoomType()))
                .sorted(Comparator.comparing((Room r) -> r.getBlockName() != null ? r.getBlockName() : "")
                        .thenComparingInt(r -> r.getFloorNumber() != null ? r.getFloorNumber() : 1)
                        .thenComparing(r -> r.getRoomCode() != null ? r.getRoomCode() : ""))
                .collect(Collectors.toList());

        // Group batches by department
        Map<String, List<Batch>> batchesByDept = new LinkedHashMap<>();
        for (Batch b : allBatches) {
            String dept = b.getDepartment() != null ? b.getDepartment().trim() : "General";
            if (departmentFilter != null && !departmentFilter.isBlank() && !departmentFilter.equalsIgnoreCase("ALL")) {
                if (!DepartmentMatcher.matches(dept, departmentFilter)) {
                    continue;
                }
            }
            batchesByDept.computeIfAbsent(dept, k -> new ArrayList<>()).add(b);
        }

        List<DepartmentRoomProposal> proposals = new ArrayList<>();
        Set<Long> globallyAllocatedRoomIds = new HashSet<>();
        List<String> globalWarnings = new ArrayList<>();

        int totalSectionsCount = 0;
        int totalAllocatedCount = 0;

        for (Map.Entry<String, List<Batch>> entry : batchesByDept.entrySet()) {
            String dept = entry.getKey();
            List<Batch> deptBatches = entry.getValue();
            deptBatches.sort(Comparator.comparing(Batch::getBatchName));

            totalSectionsCount += deptBatches.size();

            DepartmentRoomProposal prop = planDepartmentAllocation(
                    dept, deptBatches, lectureRooms, globallyAllocatedRoomIds);

            proposals.add(prop);
            totalAllocatedCount += prop.allocatedCount();

            // Track globally allocated rooms so departments do not collide
            for (SectionRoomAllocation sa : prop.allocations()) {
                if (sa.roomId() != null) {
                    globallyAllocatedRoomIds.add(sa.roomId());
                }
            }
        }

        return new FullRoomAllocationResult(proposals, totalSectionsCount, totalAllocatedCount, globalWarnings);
    }

    /**
     * Determine preferred floor and allocate rooms for a specific department.
     */
    private DepartmentRoomProposal planDepartmentAllocation(
            String department,
            List<Batch> batches,
            List<Room> allLectureRooms,
            Set<Long> globallyUsedRoomIds
    ) {
        List<String> notices = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 1. Identify rooms explicitly configured for this department in Room Master
        List<Room> deptConfiguredRooms = allLectureRooms.stream()
                .filter(r -> r.getDepartment() != null && DepartmentMatcher.matches(r.getDepartment(), department))
                .collect(Collectors.toList());

        // 2. Determine Preferred Block & Floor dynamically from Room Master
        FloorTarget preferredFloorTarget = findPreferredFloor(department, deptConfiguredRooms, allLectureRooms, globallyUsedRoomIds);

        String preferredBlock = preferredFloorTarget.blockName;
        Integer preferredFloor = preferredFloorTarget.floorNumber;
        Integer secondaryFloor = preferredFloorTarget.secondaryFloorNumber;

        // 3. Collect candidate rooms prioritized:
        // Priority A: Unallocated rooms on Preferred Floor & Block
        // Priority B: Unallocated rooms on Secondary Floor & Block
        // Priority C: Unallocated rooms on any floor of the same Block
        // Priority D: Any unallocated lecture room
        List<Room> preferredFloorRooms = new ArrayList<>();
        List<Room> secondaryFloorRooms = new ArrayList<>();
        List<Room> otherSameBlockRooms = new ArrayList<>();
        List<Room> fallbackRooms = new ArrayList<>();

        for (Room r : allLectureRooms) {
            if (globallyUsedRoomIds.contains(r.getId())) continue;

            boolean isSameBlock = Objects.equals(r.getBlockName(), preferredBlock);
            boolean isPrefFloor = isSameBlock && Objects.equals(r.getFloorNumber(), preferredFloor);
            boolean isSecFloor = isSameBlock && Objects.equals(r.getFloorNumber(), secondaryFloor);

            if (isPrefFloor) {
                preferredFloorRooms.add(r);
            } else if (isSecFloor) {
                secondaryFloorRooms.add(r);
            } else if (isSameBlock) {
                otherSameBlockRooms.add(r);
            } else {
                fallbackRooms.add(r);
            }
        }

        // Sort each pool by roomCode for predictable sequential ordering (e.g. 301, 302, 303...)
        Comparator<Room> codeComparator = Comparator.comparing((Room r) -> r.getRoomCode() != null ? r.getRoomCode() : "");
        preferredFloorRooms.sort(codeComparator);
        secondaryFloorRooms.sort(codeComparator);
        otherSameBlockRooms.sort(codeComparator);
        fallbackRooms.sort(codeComparator);

        int availableOnPrefFloor = preferredFloorRooms.size();

        if (availableOnPrefFloor >= batches.size()) {
            notices.add(String.format("Preferred %s Floor %d has %d suitable classrooms for %d sections.",
                    preferredBlock != null ? preferredBlock : "Block", preferredFloor != null ? preferredFloor : 1,
                    availableOnPrefFloor, batches.size()));
        } else if (availableOnPrefFloor > 0) {
            notices.add(String.format("Capacity overflow: Preferred Floor %d has %d classrooms for %d sections. %d section(s) will allocate to secondary Floor %s.",
                    preferredFloor != null ? preferredFloor : 1, availableOnPrefFloor, batches.size(),
                    batches.size() - availableOnPrefFloor,
                    secondaryFloor != null ? String.valueOf(secondaryFloor) : "adjacent"));
        } else {
            warnings.add(String.format("No unallocated rooms found on designated Floor %s for department [%s]. Using available institutional rooms.",
                    preferredFloor != null ? String.valueOf(preferredFloor) : "?", department));
        }

        // 4. Allocate room per section
        List<SectionRoomAllocation> allocations = new ArrayList<>();
        Set<Long> locallyAllocatedRoomIds = new HashSet<>();

        for (Batch batch : batches) {
            int strength = batch.getStrength() != null ? batch.getStrength() : 60;

            // First check if batch already has assignedRoomCode that is still available and on preferred floor
            Room selectedRoom = null;
            boolean isPreferred = true;

            // Candidate search order: Preferred floor -> Secondary floor -> Other same block -> Fallback
            selectedRoom = pickBestRoomForBatch(batch, strength, preferredFloorRooms, locallyAllocatedRoomIds);
            if (selectedRoom != null) {
                isPreferred = true;
            } else {
                selectedRoom = pickBestRoomForBatch(batch, strength, secondaryFloorRooms, locallyAllocatedRoomIds);
                if (selectedRoom != null) {
                    isPreferred = false;
                } else {
                    selectedRoom = pickBestRoomForBatch(batch, strength, otherSameBlockRooms, locallyAllocatedRoomIds);
                    if (selectedRoom != null) {
                        isPreferred = false;
                    } else {
                        selectedRoom = pickBestRoomForBatch(batch, strength, fallbackRooms, locallyAllocatedRoomIds);
                        isPreferred = false;
                    }
                }
            }

            if (selectedRoom != null) {
                locallyAllocatedRoomIds.add(selectedRoom.getId());
                boolean capValid = selectedRoom.getCapacity() == null || selectedRoom.getCapacity() >= strength;

                String statusMsg;
                if (!capValid) {
                    statusMsg = String.format("Capacity Warning: Room %s capacity (%d) < Section strength (%d)",
                            selectedRoom.getRoomCode(), selectedRoom.getCapacity(), strength);
                    warnings.add(String.format("Section [%s] allocated room [%s] with insufficient capacity (%d vs %d students).",
                            batch.getBatchName(), selectedRoom.getRoomCode(), selectedRoom.getCapacity(), strength));
                } else if (!isPreferred) {
                    statusMsg = String.format("Allocated on secondary Floor %d (%s) due to preferred floor space",
                            selectedRoom.getFloorNumber(), selectedRoom.getBlockName());
                } else {
                    statusMsg = "Allocated on designated floor with valid capacity";
                }

                allocations.add(new SectionRoomAllocation(
                        batch.getId(),
                        batch.getBatchName(),
                        batch.getSection(),
                        batch.getSemester(),
                        strength,
                        selectedRoom.getId(),
                        selectedRoom.getRoomCode(),
                        selectedRoom.getRoomName(),
                        selectedRoom.getBlockName(),
                        selectedRoom.getFloorNumber(),
                        selectedRoom.getCapacity(),
                        capValid,
                        isPreferred,
                        statusMsg
                ));
            } else {
                warnings.add(String.format("No available room could be allocated for Section [%s].", batch.getBatchName()));
                allocations.add(new SectionRoomAllocation(
                        batch.getId(),
                        batch.getBatchName(),
                        batch.getSection(),
                        batch.getSemester(),
                        strength,
                        null, null, null, null, null, null,
                        false, false, "No room available"
                ));
            }
        }

        int allocatedCount = (int) allocations.stream().filter(a -> a.roomId() != null).count();
        boolean allAllocated = allocatedCount == batches.size();
        boolean capacitySatisfied = allocations.stream().allMatch(a -> a.roomId() == null || a.capacityValid());
        boolean floorPreferenceSatisfied = allocations.stream().allMatch(a -> a.roomId() == null || a.isPreferredFloor());

        return new DepartmentRoomProposal(
                department,
                batches.size(),
                preferredBlock,
                preferredFloor,
                secondaryFloor,
                availableOnPrefFloor,
                allocatedCount,
                allAllocated,
                capacitySatisfied,
                floorPreferenceSatisfied,
                allocations,
                notices,
                warnings
        );
    }

    private Room pickBestRoomForBatch(Batch batch, int strength, List<Room> candidatePool, Set<Long> usedInRun) {
        // Pass 1: Unused room with adequate capacity
        for (Room r : candidatePool) {
            if (usedInRun.contains(r.getId())) continue;
            if (r.getCapacity() != null && r.getCapacity() >= strength) {
                return r;
            }
        }
        // Pass 2: Unused room with any capacity
        for (Room r : candidatePool) {
            if (!usedInRun.contains(r.getId())) {
                return r;
            }
        }
        return null;
    }

    private static class FloorTarget {
        String blockName;
        Integer floorNumber;
        Integer secondaryFloorNumber;

        FloorTarget(String blockName, Integer floorNumber, Integer secondaryFloorNumber) {
            this.blockName = blockName;
            this.floorNumber = floorNumber;
            this.secondaryFloorNumber = secondaryFloorNumber;
        }
    }

    /**
     * Dynamically finds the preferred floor for a department from Room Master data.
     */
    private FloorTarget findPreferredFloor(
            String department,
            List<Room> deptConfiguredRooms,
            List<Room> allLectureRooms,
            Set<Long> globallyUsedRoomIds
    ) {
        // Group configured dept rooms by (blockName, floorNumber)
        Map<String, Map<Integer, Long>> blockFloorCounts = new HashMap<>();

        for (Room r : deptConfiguredRooms) {
            String b = r.getBlockName() != null ? r.getBlockName() : "Main Block";
            int f = r.getFloorNumber() != null ? r.getFloorNumber() : 1;
            blockFloorCounts.computeIfAbsent(b, k -> new HashMap<>()).merge(f, 1L, Long::sum);
        }

        if (!blockFloorCounts.isEmpty()) {
            // Find (block, floor) with highest count of rooms for this department
            String bestBlock = null;
            Integer bestFloor = 1;
            long maxCount = -1;

            for (Map.Entry<String, Map<Integer, Long>> bEntry : blockFloorCounts.entrySet()) {
                for (Map.Entry<Integer, Long> fEntry : bEntry.getValue().entrySet()) {
                    if (fEntry.getValue() > maxCount) {
                        maxCount = fEntry.getValue();
                        bestBlock = bEntry.getKey();
                        bestFloor = fEntry.getKey();
                    }
                }
            }

            // Find secondary floor in same block (next highest or adjacent floor)
            Integer secFloor = null;
            long secMax = -1;
            Map<Integer, Long> floorsInBlock = blockFloorCounts.get(bestBlock);
            if (floorsInBlock != null) {
                for (Map.Entry<Integer, Long> fEntry : floorsInBlock.entrySet()) {
                    if (!fEntry.getKey().equals(bestFloor) && fEntry.getValue() > secMax) {
                        secMax = fEntry.getValue();
                        secFloor = fEntry.getKey();
                    }
                }
            }
            if (secFloor == null) {
                secFloor = bestFloor + 1;
            }

            return new FloorTarget(bestBlock, bestFloor, secFloor);
        }

        // If no explicit department tag, derive an available floor in allLectureRooms
        Map<String, Map<Integer, Long>> availableByBlockFloor = new HashMap<>();
        for (Room r : allLectureRooms) {
            if (globallyUsedRoomIds.contains(r.getId())) continue;
            String b = r.getBlockName() != null ? r.getBlockName() : "Main Block";
            int f = r.getFloorNumber() != null ? r.getFloorNumber() : 1;
            availableByBlockFloor.computeIfAbsent(b, k -> new HashMap<>()).merge(f, 1L, Long::sum);
        }

        String chosenBlock = "Main Block";
        Integer chosenFloor = 1;
        long maxAvail = -1;

        for (Map.Entry<String, Map<Integer, Long>> bEntry : availableByBlockFloor.entrySet()) {
            for (Map.Entry<Integer, Long> fEntry : bEntry.getValue().entrySet()) {
                if (fEntry.getValue() > maxAvail) {
                    maxAvail = fEntry.getValue();
                    chosenBlock = bEntry.getKey();
                    chosenFloor = fEntry.getKey();
                }
            }
        }

        return new FloorTarget(chosenBlock, chosenFloor, chosenFloor + 1);
    }

    /**
     * Confirms and persists the room allocation to Batches and Rooms.
     */
    @Transactional
    public Map<String, Object> confirmAllocation(String department, List<Map<String, Object>> allocationsPayload) {
        if (allocationsPayload == null || allocationsPayload.isEmpty()) {
            throw new IllegalArgumentException("No room allocations provided to confirm.");
        }

        int batchesUpdated = 0;
        int roomsUpdated = 0;

        for (Map<String, Object> item : allocationsPayload) {
            Long batchId = parseLong(item.get("batchId"));
            Long roomId = parseLong(item.get("roomId"));
            String roomCode = (String) item.get("roomCode");
            String blockName = (String) item.get("blockName");
            Integer floorNumber = parseInteger(item.get("floorNumber"));

            if (batchId == null) continue;

            Batch batch = batchRepository.findById(batchId).orElse(null);
            if (batch == null) continue;

            // Update batch home classroom
            batch.setAssignedRoomCode(roomCode);
            batch.setAssignedBlockName(blockName);
            batch.setAssignedFloorNumber(floorNumber);
            batchRepository.save(batch);
            batchesUpdated++;

            // If room is specified, tag room with assignedSection
            if (roomId != null) {
                roomRepository.findById(roomId).ifPresent(r -> {
                    r.setAssignedSection(batch.getBatchName());
                    if (batch.getDepartment() != null && (r.getDepartment() == null || r.getDepartment().isBlank() || "General".equalsIgnoreCase(r.getDepartment()))) {
                        r.setDepartment(batch.getDepartment());
                    }
                    roomRepository.save(r);
                });
                roomsUpdated++;
            }
        }

        log.info("[RoomAllocation] Confirmed {} batch room allocations across {} rooms for department [{}]",
                batchesUpdated, roomsUpdated, department != null ? department : "ALL");

        return Map.of(
                "message", "Room allocations confirmed successfully",
                "batchesUpdated", batchesUpdated,
                "roomsUpdated", roomsUpdated,
                "department", department != null ? department : "ALL"
        );
    }

    /**
     * Helper for TimetableSolverService:
     * Provides intelligent department-floor home room mapping for batches without pre-assigned rooms.
     */
    public Map<Long, Room> allocateHomeRoomsForBatches(List<Batch> batches, List<Room> lectureRooms) {
        Map<Long, Room> resultMap = new HashMap<>();
        Set<Long> assignedRoomIds = new HashSet<>();

        // Group batches by department
        Map<String, List<Batch>> byDept = new LinkedHashMap<>();
        for (Batch b : batches) {
            byDept.computeIfAbsent(b.getDepartment() != null ? b.getDepartment() : "General", k -> new ArrayList<>()).add(b);
        }

        for (Map.Entry<String, List<Batch>> entry : byDept.entrySet()) {
            String dept = entry.getKey();
            List<Batch> deptBatches = entry.getValue();

            DepartmentRoomProposal prop = planDepartmentAllocation(dept, deptBatches, lectureRooms, assignedRoomIds);
            for (SectionRoomAllocation sa : prop.allocations()) {
                if (sa.roomId() != null) {
                    assignedRoomIds.add(sa.roomId());
                    lectureRooms.stream()
                            .filter(r -> r.getId().equals(sa.roomId()))
                            .findFirst()
                            .ifPresent(r -> resultMap.put(sa.batchId(), r));
                }
            }
        }

        return resultMap;
    }

    private Long parseLong(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).longValue();
        try {
            return Long.parseLong(val.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseInteger(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).intValue();
        try {
            return Integer.parseInt(val.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Determines if a room type is a laboratory.
     * Consistent with TimetableSolverService and TimetableValidationService.
     */
    private boolean isLabRoom(String roomType) {
        if (roomType == null || roomType.isBlank()) return false;
        String upper = roomType.toUpperCase().trim();
        return upper.equals("LAB") || upper.equals("LABORATORY") || upper.contains("_LAB") || upper.contains("LAB_");
    }
}
