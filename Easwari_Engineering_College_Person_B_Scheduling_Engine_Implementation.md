# Easwari Engineering College — Person B Work Specification
## Scheduling Engine & Automated Timetable Generation — Implementation Document

> **Project Name:** Easwari Engineering College — Academic Management and Smart Timetable Scheduler  
> **Module:** Person B — Scheduling Engine, Timetable Solver, & Contract Integration  
> **Status:** Integrated & Verified  
> **Boundary:** Consumes Person A's validated data contract (`/api/scheduler/input`) and implements the authoritative constraint-satisfaction scheduling engine.

---

## 1. Architectural Role and Scope

Person B is responsible for the automated timetable generation engine, constraint satisfaction solver, section timetable synthesis, and integration with Person A's data layer:

1. **Contract Ingestion**: Consuming `SchedulerInputContractDto` from `GET /api/scheduler/input` or database entities validated by `DataReadinessService`.
2. **Constraint Formulation**:
   - Hard constraints (no faculty double-booking, no classroom double-booking, no section double-booking, room capacity/type matching, lab continuity, faculty availability and eligibility).
   - Soft constraints (schedule balance, consistent room allocations, minimizing gaps, geographic room clustering).
3. **Solver Pipeline**:
   - Async solver lifecycle managed via `TimetableJob` (`QUEUED` -> `IN_PROGRESS` -> `COMPLETED` / `FAILED`).
   - Lab block contiguous period scheduling (2–3 consecutive slots avoiding break/lunch splits).
   - Special academic period allocations (Aptitude, Technical Test, NPTEL, Counselling, LeetCode, Career Skills).
4. **Validation and Dual Persistence**:
   - Post-solve verification through `TimetableValidationService`.
   - Dual persistence into `TimetableEntry` (for high-speed matrix rendering) and `TimetableAssignment` (for entity-relational integrity and room changes).
5. **Interactive Operations**:
   - Classroom adjustment and swap endpoints with capacity checking and history logging (`POST /api/timetable/change-room`).
   - Reverting to primary designated room (`POST /api/timetable/restore-room`).

---

## 2. Integration Interface Boundary (Person A ↔ Person B)

Person B consumes Person A's validated data layer via:

- **Endpoint**: `GET /api/scheduler/input`
- **DTO**: `com.odysseus.backend.dto.SchedulerInputContractDto`
- **Readiness Verification**: `GET /api/scheduler/readiness` ensures all prerequisite data (subjects, faculty, rooms, timeslots, calendar rules) are ready before solving begins.

### JSON Contract Elements

```json
{
  "metadata": {
    "academicYear": "2026-2027",
    "semester": 5,
    "institution": "Easwari Engineering College",
    "contractVersion": "1.0.0",
    "generatedAt": "2026-10-27T10:00:00"
  },
  "subjects": [ ... ],
  "faculty": [ ... ],
  "rooms": [ ... ],
  "batches": [ ... ],
  "offerings": [ ... ],
  "timeslots": [ ... ]
}
```

---

## 3. Constraint-Satisfaction Solver Implementation

The active scheduling engine is implemented in:
`com.odysseus.backend.service.TimetableSolverService`

### 3.1 Hard Constraints (Zero-Tolerance)
1. **Section Non-Overlap**: A class section cannot have two subjects in the same timeslot.
2. **Faculty Non-Overlap**: A faculty member cannot be scheduled to teach more than one section during the same timeslot.
3. **Classroom Non-Overlap**: A physical room or lab cannot host multiple sections concurrently.
4. **Room Type & Capacity**:
   - Theory courses must be assigned to `LECTURE_HALL` or general classrooms where `capacity >= batch.strength`.
   - Laboratory sessions must be assigned to rooms tagged with `LAB` or matching practical equipment.
5. **Continuous Laboratory Sessions**:
   - Practical lab demands (2 to 3 consecutive hours) must be scheduled in consecutive timeslots without spanning across morning tea break or lunch break.
6. **Faculty Availability & Workload**:
   - Faculty cannot be booked in slots marked unavailable in `FacultyAvailability`.
   - Total scheduled hours must not exceed `faculty.maxWeeklyLoad`.
7. **Holiday and Calendar Respect**:
   - Generation excludes unavailable dates, institutional holidays, and non-working days defined in `AcademicCalendar` and `CalendarHoliday`.

### 3.2 Infeasibility & Conflict Reporting
If constraints cannot be satisfied (e.g. insufficient lab rooms or overloaded faculty), the solver logs specific conflicting entities and marks the `TimetableJob` with `FAILED` and clear diagnostic messages rather than generating corrupt or silently truncated schedules.

---

## 4. API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/scheduler/input` | Exports validated Person A data contract JSON |
| `GET` | `/api/scheduler/readiness` | Checks data completeness prior to generation |
| `POST` | `/api/scheduler/generate` | Starts async timetable generation solver job |
| `GET` | `/api/scheduler/status/{jobId}` | Checks job execution status (`QUEUED`, `IN_PROGRESS`, `COMPLETED`, `FAILED`) |
| `GET` | `/api/scheduler/result/{jobId}` | Retrieves entries generated by the specific solver job |
| `GET` | `/api/timetable/batch/{batchId}` | Retrieves active weekly timetable for a student batch |
| `GET` | `/api/timetable/faculty/{facultyId}` | Retrieves active schedule for a faculty member |
| `POST` | `/api/timetable/change-room` | Adjusts or swaps classroom with capacity validation |
| `POST` | `/api/timetable/restore-room` | Restores usual classroom allocation from audit history |

---

## 5. Verification & Testing

- **Backend Startup**: Spring Boot backend boots with H2 PostgreSQL mode with all master entities and constraints registered.
- **REST Contract**: Tested and verified on `http://localhost:8080/api/scheduler/input` and `http://localhost:8080/api/auth/login`.
- **Frontend Interaction**: React + Vite frontend connects via reverse proxy to port 8080 to display timetables and submit generation requests.
