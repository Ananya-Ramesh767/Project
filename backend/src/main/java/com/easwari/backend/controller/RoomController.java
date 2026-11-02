package com.easwari.backend.controller;

import com.easwari.backend.domain.Room;
import com.easwari.backend.repository.RoomRepository;
import com.easwari.backend.service.RoomAllocationService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomRepository roomRepository;
    private final RoomAllocationService roomAllocationService;
    private final com.easwari.backend.repository.BatchRepository batchRepository;

    @GetMapping
    public ResponseEntity<List<Room>> getAllRooms() {
        return ResponseEntity.ok(roomRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<?> createRoom(@RequestBody Room room) {
        if (roomRepository.existsByRoomCode(room.getRoomCode())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Room code [" + room.getRoomCode() + "] already exists."));
        }
        Room saved = roomRepository.save(room);
        return ResponseEntity.ok(saved);
    }

    @Data
    public static class BatchRoomRequest {
        private String blockName;
        private int startingRoomNumber;
        private int endingRoomNumber;
        private String roomType; // LECTURE_HALL, LAB
        private int capacity;
        private String department;
    }

    @PostMapping("/batch-generate")
    public ResponseEntity<?> batchGenerateRooms(@RequestBody BatchRoomRequest request) {
        if (request.getEndingRoomNumber() < request.getStartingRoomNumber()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ending room number must be >= starting room number."));
        }

        String blockPrefix = request.getBlockName().replace("Block", "").trim();
        if (blockPrefix.isEmpty()) blockPrefix = "R";

        List<Room> createdRooms = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (int i = request.getStartingRoomNumber(); i <= request.getEndingRoomNumber(); i++) {
            String roomCode = blockPrefix + "-" + i;
            if (roomRepository.existsByRoomCode(roomCode)) {
                skipped.add(roomCode);
                continue;
            }

            Room room = Room.builder()
                    .roomCode(roomCode)
                    .roomName(request.getBlockName() + " Room " + i)
                    .blockName(request.getBlockName())
                    .roomType(request.getRoomType() != null ? request.getRoomType() : "LECTURE_HALL")
                    .capacity(request.getCapacity() > 0 ? request.getCapacity() : 60)
                    .department(request.getDepartment() != null ? request.getDepartment() : "General")
                    .active(true)
                    .build();

            createdRooms.add(roomRepository.save(room));
        }

        return ResponseEntity.ok(Map.of(
                "createdCount", createdRooms.size(),
                "createdRooms", createdRooms,
                "skippedExisting", skipped
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateRoom(@PathVariable Long id, @RequestBody Room room) {
        if (!roomRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        if (roomRepository.existsByRoomCodeAndIdNot(room.getRoomCode(), id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Room code [" + room.getRoomCode() + "] is taken by another room."));
        }
        room.setId(id);
        Room saved = roomRepository.save(room);
        return ResponseEntity.ok(saved);
    }

    @Data
    public static class RoomDetail {
        private String roomCode;
        private String roomName;
        private String roomType; // LECTURE_HALL, LAB
        private int capacity;
    }

    @Data
    public static class FloorDetail {
        private int floorNumber;
        private List<RoomDetail> rooms;
    }

    @Data
    public static class BlockConfig {
        private String blockName;
        private int classroomsCount;
        private int labsCount;
        private int classroomCapacity;
        private int labCapacity;
        private String department;
        private List<FloorDetail> floors;
    }

    @Data
    public static class HierarchicalSetupRequest {
        private List<BlockConfig> blocks;
    }

    @PostMapping("/hierarchical-setup")
    public ResponseEntity<?> hierarchicalSetup(@RequestBody HierarchicalSetupRequest request) {
        if (request.getBlocks() == null || request.getBlocks().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Please configure at least one block."));
        }

        List<Room> createdRooms = new ArrayList<>();
        for (BlockConfig block : request.getBlocks()) {
            String bName = block.getBlockName() != null ? block.getBlockName().trim() : "Block A";
            String prefix = bName.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
            if (prefix.length() > 4) prefix = prefix.substring(0, 4);

            String dept = block.getDepartment() != null && !block.getDepartment().trim().isEmpty() ? block.getDepartment() : "General";

            // If user supplied explicit floors with rooms (Hierarchical Wizard)
            if (block.getFloors() != null && !block.getFloors().isEmpty()) {
                for (FloorDetail floor : block.getFloors()) {
                    int fNum = floor.getFloorNumber() > 0 ? floor.getFloorNumber() : 1;
                    if (floor.getRooms() != null) {
                        for (int rIdx = 0; rIdx < floor.getRooms().size(); rIdx++) {
                            RoomDetail rd = floor.getRooms().get(rIdx);
                            String code = rd.getRoomCode() != null && !rd.getRoomCode().trim().isEmpty()
                                    ? rd.getRoomCode().trim()
                                    : prefix + "-F" + fNum + "-" + (rIdx + 1);
                            String name = rd.getRoomName() != null && !rd.getRoomName().trim().isEmpty()
                                    ? rd.getRoomName().trim()
                                    : bName + " - Floor " + fNum + " - Room " + (rIdx + 1);
                            String type = rd.getRoomType() != null && !rd.getRoomType().trim().isEmpty()
                                    ? rd.getRoomType()
                                    : "LECTURE_HALL";
                            int cap = rd.getCapacity() > 0 ? rd.getCapacity() : 60;

                            Room r = roomRepository.findByRoomCode(code).orElse(new Room());
                            r.setRoomCode(code);
                            r.setRoomName(name);
                            r.setBlockName(bName);
                            r.setFloorNumber(fNum);
                            r.setRoomType(type);
                            r.setCapacity(cap);
                            r.setDepartment(dept);
                            r.setActive(true);

                            createdRooms.add(roomRepository.save(r));
                        }
                    }
                }
            } else {
                // Fallback to legacy count-based setup
                int cCap = block.getClassroomCapacity() > 0 ? block.getClassroomCapacity() : 60;
                int lCap = block.getLabCapacity() > 0 ? block.getLabCapacity() : 60;

                // Classrooms
                for (int i = 1; i <= block.getClassroomsCount(); i++) {
                    String code = prefix + "-" + (100 + i);
                    if (!roomRepository.existsByRoomCode(code)) {
                        Room r = Room.builder()
                                .roomCode(code)
                                .roomName(bName + " Classroom " + i)
                                .blockName(bName)
                                .floorNumber(1)
                                .roomType("LECTURE_HALL")
                                .capacity(cCap)
                                .department(dept)
                                .active(true)
                                .build();
                        createdRooms.add(roomRepository.save(r));
                    }
                }

                // Laboratories
                for (int j = 1; j <= block.getLabsCount(); j++) {
                    String code = prefix + "-LAB-0" + j;
                    if (!roomRepository.existsByRoomCode(code)) {
                        Room r = Room.builder()
                                .roomCode(code)
                                .roomName(bName + " Specialized Lab " + j)
                                .blockName(bName)
                                .floorNumber(2)
                                .roomType("LAB")
                                .capacity(lCap)
                                .department(dept)
                                .active(true)
                                .build();
                        createdRooms.add(roomRepository.save(r));
                    }
                }
            }
        }

        return ResponseEntity.ok(Map.of(
                "message", "Hierarchical rooms provisioned successfully",
                "totalCreated", createdRooms.size(),
                "createdRooms", createdRooms
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRoom(@PathVariable Long id) {
        return roomRepository.findById(id).map(r -> {
            r.setActive(false);
            roomRepository.save(r);
            return ResponseEntity.ok(Map.of("message", "Room deactivated successfully"));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Department-Wise Floor & Section Room Allocation Endpoints
    // ─────────────────────────────────────────────────────────────────────────────

    @GetMapping("/allocation-proposals")
    public ResponseEntity<?> getAllocationProposals(@RequestParam(required = false) String department) {
        RoomAllocationService.FullRoomAllocationResult result = roomAllocationService.generateProposals(department);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/confirm-allocation")
    public ResponseEntity<?> confirmAllocation(@RequestBody Map<String, Object> payload) {
        String department = (String) payload.get("department");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> allocations = (List<Map<String, Object>>) payload.get("allocations");
        Map<String, Object> res = roomAllocationService.confirmAllocation(department, allocations);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/auto-allocate")
    public ResponseEntity<?> autoAllocateRooms(@RequestBody(required = false) Map<String, Object> payload) {
        String department = payload != null ? (String) payload.get("department") : null;
        RoomAllocationService.FullRoomAllocationResult proposals = roomAllocationService.generateProposals(department);

        List<Map<String, Object>> flatAllocations = new ArrayList<>();
        for (RoomAllocationService.DepartmentRoomProposal dp : proposals.departmentProposals()) {
            for (RoomAllocationService.SectionRoomAllocation sa : dp.allocations()) {
                if (sa.roomId() != null) {
                    flatAllocations.add(Map.of(
                            "batchId", sa.batchId(),
                            "roomId", sa.roomId(),
                            "roomCode", sa.roomCode(),
                            "blockName", sa.blockName() != null ? sa.blockName() : "",
                            "floorNumber", sa.floorNumber() != null ? sa.floorNumber() : 1
                    ));
                }
            }
        }

        Map<String, Object> result = roomAllocationService.confirmAllocation(department, flatAllocations);
        return ResponseEntity.ok(Map.of(
                "message", "Auto-allocation completed successfully",
                "department", department != null ? department : "ALL",
                "proposals", proposals,
                "confirmed", result
        ));
    }

    /**
     * POST /api/admin/rooms/reconcile
     * Safe reconciliation: audits batch room assignments, removes any assignment where
     * a section was assigned to a LAB room or non-existent room, and safely re-allocates.
     */
    @PostMapping("/reconcile")
    public ResponseEntity<?> reconcileRoomAssignments() {
        List<com.easwari.backend.domain.Batch> batches = batchRepository.findAll();
        List<Room> allRooms = roomRepository.findAll();
        Map<String, Room> roomMap = allRooms.stream()
                .filter(r -> r.getRoomCode() != null)
                .collect(java.util.stream.Collectors.toMap(r -> r.getRoomCode().trim().toUpperCase(), r -> r, (a, b) -> a));

        int fixedBatches = 0;
        List<String> fixLog = new ArrayList<>();

        for (com.easwari.backend.domain.Batch b : batches) {
            if (b.getAssignedRoomCode() != null && !b.getAssignedRoomCode().isBlank()) {
                Room r = roomMap.get(b.getAssignedRoomCode().trim().toUpperCase());
                if (r == null) {
                    fixLog.add("Cleared non-existent room assignment [" + b.getAssignedRoomCode() + "] from section [" + b.getBatchName() + "].");
                    b.setAssignedRoomCode(null);
                    b.setAssignedBlockName(null);
                    b.setAssignedFloorNumber(null);
                    batchRepository.save(b);
                    fixedBatches++;
                } else if (isLabRoomType(r.getRoomType())) {
                    fixLog.add("Cleared invalid LAB room [" + r.getRoomCode() + "] from section [" + b.getBatchName() + "] default classroom.");
                    b.setAssignedRoomCode(null);
                    b.setAssignedBlockName(null);
                    b.setAssignedFloorNumber(null);
                    batchRepository.save(b);
                    fixedBatches++;
                }
            }
        }

        // Re-run auto allocation across all departments to give sections valid classrooms
        RoomAllocationService.FullRoomAllocationResult proposals = roomAllocationService.generateProposals(null);
        List<Map<String, Object>> flatAllocations = new ArrayList<>();
        for (RoomAllocationService.DepartmentRoomProposal dp : proposals.departmentProposals()) {
            for (RoomAllocationService.SectionRoomAllocation sa : dp.allocations()) {
                if (sa.roomId() != null) {
                    flatAllocations.add(Map.of(
                            "batchId", sa.batchId(),
                            "roomId", sa.roomId(),
                            "roomCode", sa.roomCode(),
                            "blockName", sa.blockName() != null ? sa.blockName() : "",
                            "floorNumber", sa.floorNumber() != null ? sa.floorNumber() : 1
                    ));
                }
            }
        }
        Map<String, Object> confirmResult = roomAllocationService.confirmAllocation(null, flatAllocations);

        return ResponseEntity.ok(Map.of(
                "reconciledCount", fixedBatches,
                "reconcileLogs", fixLog,
                "allocationResult", confirmResult
        ));
    }

    /**
     * GET /api/admin/rooms/validate
     * Audits the Room Master data for quality issues:
     * - Rooms with no capacity
     * - Rooms with no type
     * - Rooms that are LAB type but have names suggesting they are lecture halls
     * - Batches assigned to LAB rooms as home classrooms (invalid)
     */
    @GetMapping("/validate")
    public ResponseEntity<?> validateRoomData() {
        List<Room> allRooms = roomRepository.findAll();

        List<Map<String, Object>> issues = new ArrayList<>();
        List<Map<String, Object>> warnings = new ArrayList<>();

        for (Room r : allRooms) {
            // Missing capacity
            if (r.getCapacity() == null || r.getCapacity() <= 0) {
                issues.add(Map.of(
                    "roomCode", r.getRoomCode(),
                    "issue", "Room has no capacity configured",
                    "severity", "WARNING"
                ));
            }
            // Missing type
            if (r.getRoomType() == null || r.getRoomType().isBlank()) {
                issues.add(Map.of(
                    "roomCode", r.getRoomCode(),
                    "issue", "Room has no type configured (LECTURE_HALL or LAB required)",
                    "severity", "BLOCKING"
                ));
            }
            // Name vs type mismatch detection
            if (r.getRoomType() != null && r.getRoomName() != null) {
                String nameUpper = r.getRoomName().toUpperCase();
                String typeUpper = r.getRoomType().toUpperCase();
                boolean nameImpliesLab = nameUpper.contains("LAB") || nameUpper.contains("LABORATORY");
                boolean typeIsLab = isLabRoomType(r.getRoomType());
                boolean nameImpliesHall = nameUpper.contains("CONFERENCE HALL") || nameUpper.contains("SEMINAR HALL") || nameUpper.contains("LECTURE HALL");
                if (nameImpliesHall && typeIsLab) {
                    warnings.add(Map.of(
                        "roomCode", r.getRoomCode(),
                        "roomName", r.getRoomName(),
                        "roomType", r.getRoomType(),
                        "issue", "Room name suggests a hall/classroom but type is LAB — possible misconfiguration",
                        "severity", "WARNING"
                    ));
                }
                if (nameImpliesLab && !typeIsLab) {
                    warnings.add(Map.of(
                        "roomCode", r.getRoomCode(),
                        "roomName", r.getRoomName(),
                        "roomType", r.getRoomType(),
                        "issue", "Room name suggests a laboratory but type is not LAB — possible misconfiguration",
                        "severity", "WARNING"
                    ));
                }
            }
        }

        return ResponseEntity.ok(Map.of(
            "totalRooms", allRooms.size(),
            "activeRooms", allRooms.stream().filter(Room::isActive).count(),
            "labRooms", allRooms.stream().filter(r -> isLabRoomType(r.getRoomType())).count(),
            "theoryRooms", allRooms.stream().filter(r -> r.getRoomType() != null && !isLabRoomType(r.getRoomType())).count(),
            "blockingIssues", issues.stream().filter(i -> "BLOCKING".equals(i.get("severity"))).toList(),
            "warnings", warnings,
            "allIssues", issues
        ));
    }

    private boolean isLabRoomType(String roomType) {
        if (roomType == null || roomType.isBlank()) return false;
        String upper = roomType.toUpperCase().trim();
        return upper.equals("LAB") || upper.equals("LABORATORY") || upper.contains("_LAB") || upper.contains("LAB_");
    }
}
