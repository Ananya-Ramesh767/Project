# SCHEDULER DATA CONTRACT & INTEGRATION BOUNDARY

## Overview
This document specifies the exact JSON data contract exposed by **Person A (Admin & Data Layer)** for consumption by **Person B (Scheduling & Timetable Engine)** in the **Easwari Engineering College Timetable Scheduler and Lesson Planner**.

- **API Endpoint**: `GET /api/scheduler/input`
- **Format**: `application/json`
- **Authentication**: Public or Bearer Token (configurable)
- **Status**: Validated by Person A's Data Readiness Engine before handoff.

---

## High-Level Contract Schema

```json
{
  "metadata": {
    "academicYear": "2026-2027",
    "semester": 5,
    "institution": "Easwari Engineering College",
    "contractVersion": "1.0.0",
    "generatedAt": "2026-09-07T11:30:00"
  },
  "subjects": [
    {
      "subjectId": 1,
      "subjectCode": "CS25101",
      "subjectName": "Data Structures & Algorithms",
      "department": "CSE",
      "semester": 5,
      "subjectType": "THEORY_AND_LAB",
      "theoryCredits": 3.0,
      "labCredits": 1.0,
      "tutorialCredits": 0.0,
      "theoryHoursPerWeek": 3.0,
      "labHoursPerWeek": 2.0,
      "tutorialHoursPerWeek": 0.0,
      "totalWeeklyHours": 5.0,
      "requiresLab": true,
      "labDurationPeriods": 2
    }
  ],
  "faculty": [
    {
      "facultyId": 1,
      "employeeId": "EMP101",
      "name": "Dr. Alan Turing",
      "email": "turing@odysseus.edu",
      "department": "CSE",
      "maxWeeklyLoad": 18,
      "eligibleSubjectIds": [1, 2]
    }
  ],
  "rooms": [
    {
      "roomId": 1,
      "roomCode": "LH-101",
      "roomName": "Lecture Hall 101",
      "roomType": "LECTURE_HALL",
      "capacity": 70,
      "department": "CSE"
    },
    {
      "roomId": 2,
      "roomCode": "LAB-201",
      "roomName": "Computer Systems Lab",
      "roomType": "LAB",
      "capacity": 60,
      "department": "CSE"
    }
  ],
  "batches": [
    {
      "batchId": 1,
      "batchName": "CSE 5th Sem - Section A",
      "section": "A",
      "department": "CSE",
      "semester": 5,
      "strength": 60
    }
  ],
  "offerings": [
    {
      "offeringId": 1,
      "batchId": 1,
      "subjectId": 1,
      "assignedFacultyId": 1,
      "eligibleFacultyIds": [1]
    }
  ],
  "timeslots": [
    {
      "timeslotId": 1,
      "dayOfWeek": "MONDAY",
      "periodNumber": 1,
      "periodLabel": "P1",
      "startTime": "08:15",
      "endTime": "09:05",
      "isBreak": false,
      "isLunch": false
    },
    {
      "timeslotId": 3,
      "dayOfWeek": "MONDAY",
      "periodNumber": 0,
      "periodLabel": "BREAK",
      "startTime": "09:55",
      "endTime": "10:10",
      "isBreak": true,
      "isLunch": false
    }
  ]
}
```

---

## Field Specifications & Boundary Rules

### 1. Subjects (`subjects`)
* `subjectId` *(Long)*: Unique primary identifier.
* `subjectCode` *(String)*: Unique official course code (e.g. `CS25101`).
* `subjectType` *(String)*: `THEORY`, `LAB`, `TUTORIAL`, or `THEORY_AND_LAB`.
* `totalWeeklyHours` *(Double)*: Automatically calculated total required teaching periods per week based on configured NEP rules.
* `requiresLab` *(Boolean)*: `true` if practical lab sessions are required.
* `labDurationPeriods` *(Integer)*: Number of continuous periods required for lab sessions (e.g., 2 or 3).
  - **Constraint Rule**: Lab blocks must be continuous and **cannot cross** Morning Break (`09:55-10:10`) or Lunch (`12:40-13:30`).

### 2. Faculty (`faculty`)
* `facultyId` *(Long)*: Primary identifier.
* `maxWeeklyLoad` *(Integer)*: Maximum teaching periods allowed per week. Person B must enforce this hard constraint.
* `eligibleSubjectIds` *(Array of Long)*: List of subject IDs this faculty member is qualified to teach.

### 3. Rooms (`rooms`)
* `roomType` *(String)*: `LECTURE_HALL` or `LAB`.
* `capacity` *(Integer)*: Maximum student capacity.
  - **Constraint Rule**: Person B must ensure `room.capacity >= batch.strength`. Lab subjects require `roomType = LAB`.

### 4. Batches (`batches`) & Offerings (`offerings`)
* `batchId` *(Long)* & `subjectId` *(Long)*: Connects student groups to their required subjects for the semester.
* `eligibleFacultyIds` *(Array of Long)*: List of active faculty eligible to teach this specific subject offering.

### 5. Timeslots (`timeslots`)
* Represents the exact college schedule:
  - `P1`: 08:15 – 09:05 (50 min)
  - `P2`: 09:05 – 09:55 (50 min)
  - `BREAK`: 09:55 – 10:10 (15 min, `isBreak: true`, Non-teaching)
  - `P3`: 10:10 – 11:00 (50 min)
  - `P4`: 11:00 – 11:50 (50 min)
  - `P5`: 11:50 – 12:40 (50 min)
  - `LUNCH`: 12:40 – 13:30 (50 min, `isLunch: true`, Non-teaching)
  - `P6`: 13:30 – 14:15 (45 min)
  - `P7`: 14:15 – 15:00 (45 min)
  - `P8`: 15:00 – 15:45 (45 min)

---

## Integration Boundary for Person B
To connect Person B's scheduling solver (e.g. OptaPlanner):
1. Send `GET /api/scheduler/input` to fetch validated master data.
2. Run solver optimization on Person B's service.
3. Post generated timetable results to Person B's schedule storage/display API.

Person A's Admin UI contains a **Generate Timetable** button (`POST /api/scheduler/generate`) that validates readiness and displays `"Scheduler service not connected yet. Data validation passed."` until Person B's service is linked.
