# Easwari Engineering College — Person A Work Specification
## Data Layer & Admin Setup — Antigravity Implementation Specification

> **Project Name:** Easwari Engineering College — Academic Management and Smart Timetable Scheduler
> **Purpose:** This document is the exact implementation specification for **Person A** in the Easwari Engineering College timetable project.
>
> **Important boundary:** Person A owns the **data/input side** of the timetable system. Person B owns the **scheduling/generation engine**. Do NOT implement or rewrite Person B's OptaPlanner scheduling logic.

---

# 1. ROLE AND RESPONSIBILITY

You are implementing **Person A's portion only**:

1. Login/authentication foundation
2. Role-based access
3. Master data management
4. Academic calendar and scheduling availability data
5. Configurable NEP credit-to-hours rules
6. Automatic weekly-hour calculation
7. Admin data-entry UI
8. Validation and persistence of all input data
9. A clean, documented data contract for Person B's scheduler
10. Integration points/placeholders for the Generate Timetable action

The final result must provide clean, validated data that another developer can directly consume in the timetable-generation engine.

---

# 2. FIRST RULE — INSPECT THE EXISTING PROJECT BEFORE CHANGING ANYTHING

Before creating, modifying, deleting, renaming, or moving files:

- Inspect the **entire existing project structure**.
- Identify the frontend framework and backend/database architecture, if already present.
- Inspect:
  - `index.html`
  - `app.js`
  - `styles.css`
  - all existing Java/backend files
  - configuration files
  - existing routes/APIs
  - existing database/schema files
  - existing components/pages
  - existing authentication
  - existing timetable-related code
- Determine what is already implemented.
- Reuse the existing architecture and styling wherever possible.
- Do NOT replace working code unnecessarily.
- Do NOT create duplicate implementations of features that already exist.
- Do NOT modify Person B's scheduling engine unless absolutely required for the agreed data contract.

### Before implementation, produce an internal plan containing:
- Existing architecture
- Existing relevant files
- Features already implemented
- Files that must be changed
- Files that must be created
- Integration points with Person B
- Risks/conflicts with existing code

Then implement the work.

---

# 3. STRICT TEAM BOUNDARY

## Person A owns

### Authentication/Data side
- Admin login
- Faculty login foundation
- Role-based access
- Admin dashboard
- Subject master
- Credit information
- Faculty master
- Faculty-subject teaching eligibility
- Faculty maximum workload
- Room master
- Batch/section master
- Subject offering/assignment data needed to connect subjects to batches
- Academic calendar
- Working-day rules
- Saturday/Sunday rules
- Government holidays
- College holidays
- Scheduling availability/timeslot definitions
- NEP credit-to-hours configuration
- Weekly-hour calculation
- Data validation
- CRUD operations
- Persistence
- Data export/API/data contract for Person B

## Person B owns

- OptaPlanner implementation
- Constraint streams
- Timetable optimization
- Timetable generation algorithm
- Hard/soft scheduling constraints
- Solver configuration
- Optimization scoring
- Automatic timetable generation logic

### Do NOT:
- Rewrite Person B's scheduler.
- Create a second timetable-generation algorithm.
- Hardcode scheduling logic into the admin forms.
- Mix UI CRUD code with OptaPlanner constraint logic.

---

# 4. AUTHENTICATION AND ROLE-BASED ACCESS

Implement a proper login foundation.

## Roles

### ADMIN
Can:
- Login
- Access admin dashboard
- Create/edit/delete master data
- Configure NEP rules
- Manage academic calendar
- View calculated weekly hours
- Validate data
- Access timetable generation entry point/button
- View system data

### FACULTY
For now:
- Login
- Have a separate role
- Be restricted from admin CRUD pages
- Have the foundation for a future faculty dashboard/lesson-plan module

Do not build the complete Faculty lesson-plan module unless it already exists.

## Security requirements

- Never store plain-text passwords if a backend/database exists.
- Use the existing authentication architecture if one exists.
- Protect admin routes/pages from faculty users.
- Prevent a user from simply typing an admin URL and bypassing role checks.
- Keep authentication logic separate from master-data logic.

If the existing project already has authentication, improve/reuse it instead of replacing it.

---

# 5. ADMIN DASHBOARD

Create/maintain an Admin Dashboard with clear navigation to:

1. Subjects
2. Faculty
3. Rooms
4. Batches / Sections
5. Subject Offerings / Batch Assignments
6. Academic Calendar
7. Working Days / Timeslots
8. NEP Credit Rules
9. Timetable Generation entry point
10. Data Validation / Readiness Check

The UI should make the workflow obvious:

**Enter master data → Enter credits → Calculate weekly hours → Validate → Data ready for scheduler → Generate timetable**

---

# 6. MASTER DATA MANAGEMENT

All master data must support:

- Create
- Read
- Update
- Delete
- Search/filter where useful
- Form validation
- Duplicate prevention
- Persistence
- Clear error messages
- Confirmation before destructive deletion

---

# 7. SUBJECT MASTER

Create a Subject entity/form containing at minimum:

```text
subjectId
subjectCode
subjectName
department
semester
subjectType
theoryCredits
labCredits
tutorialCredits
requiresLab
labDuration
calculatedWeeklyHours
active
```

### Subject type examples

- THEORY
- LAB
- TUTORIAL
- THEORY_AND_LAB
- OTHER (only if required by the project)

Do not assume one universal structure if the existing project already defines one.

### Validation

- Subject code must be unique within the relevant academic context.
- Subject name cannot be empty.
- Department cannot be empty.
- Semester must be valid.
- Credits cannot be negative.
- Credits must be numeric.
- Lab-related fields must be consistent with `requiresLab`.
- Weekly hours must be calculated automatically.
- Do not allow invalid combinations such as lab credits without appropriate lab configuration unless the selected curriculum explicitly allows it.

---

# 8. CREDIT ENTRY

The admin must be able to enter separately:

```text
Theory Credits
Lab Credits
Tutorial Credits
```

As the admin types/changes credits, show:

```text
Calculated Theory Hours / Week
Calculated Lab Hours / Week
Calculated Tutorial Hours / Week
TOTAL REQUIRED HOURS / WEEK
```

The calculation must update immediately in the UI.

Do NOT hardcode the conversion inside the Subject form.

---

# 9. NEP CREDIT-TO-HOURS RULE ENGINE

This is a key part of Person A's work.

The conversion rules must be **configurable data**, not hardcoded constants.

Example:

```text
Theory:   1 credit = 1 hour/week
Lab:      1 credit = 2 hours/week
Tutorial: 1 credit = 1 hour/week
```

These are ONLY examples.

The actual institution/university rules may differ.

## Suggested rule structure

```text
ruleId
ruleName
academicYear
program
department (optional)
semester (optional)
theoryHoursPerCredit
labHoursPerCredit
tutorialHoursPerCredit
effectiveFrom
effectiveTo
active
```

The system must allow an admin to change the rule without changing source code.

## Calculation

Conceptually:

```text
theoryHours =
    theoryCredits × theoryHoursPerCredit

labHours =
    labCredits × labHoursPerCredit

tutorialHours =
    tutorialCredits × tutorialHoursPerCredit

totalWeeklyHours =
    theoryHours + labHours + tutorialHours
```

Use the active rule applicable to the subject's academic context.

### Important

Do NOT label an arbitrary conversion as an official NEP rule unless it has been configured by the institution.

The UI should clearly indicate that the conversion is based on the currently configured institutional rule.

---

# 10. LAB SESSION INFORMATION

Because Person B's scheduler needs to know whether a class requires a lab:

Store enough information for the scheduler to distinguish:

- Theory
- Lab
- Tutorial

For lab subjects, support:

```text
requiresLab = true
labDuration
requiredRoomType = LAB
```

Do not implement the actual scheduling of the continuous lab block. Person B will handle that.

Your responsibility is to provide clean input data.

---

# 11. FACULTY MASTER

Create:

```text
facultyId
name
employeeId
department
email
subjectsTheyCanTeach
maxWeeklyLoad
active
```

The `subjectsTheyCanTeach` relationship should preferably be represented as a proper relation/assignment rather than storing a comma-separated string.

Example:

```text
Faculty A
  → Java
  → Data Structures
  → DBMS
```

Store maximum weekly load because Person B's scheduler needs this information.

Validation:

- Employee ID must be unique.
- Name required.
- Department required.
- Max weekly load cannot be negative.
- Assigned subjects must exist.
- Inactive faculty should not be sent as available scheduling resources.

---

# 12. ROOM MASTER

Create:

```text
roomId
roomName
roomCode
roomType
capacity
department (optional)
active
```

Room types:

```text
LECTURE_HALL
LAB
```

Validation:

- Room code/name unique.
- Capacity must be positive.
- Room type required.
- Lab classes must be able to reference LAB rooms.
- Inactive rooms must not be treated as available scheduling resources.

---

# 13. BATCH / SECTION MASTER

Create:

```text
batchId
batchName
section
department
semester
strength
academicYear
active
```

Validation:

- Batch/section identity should be unique within the relevant academic context.
- Strength must be positive.
- Semester must be valid.
- Department required.

---

# 14. SUBJECT OFFERING / BATCH-SUBJECT MAPPING

Do NOT assume that merely having a Subject record means every batch takes that subject.

Create a relationship that can represent:

```text
Batch A → Subject X
Batch A → Subject Y
Batch B → Subject X
```

Suggested structure:

```text
offeringId
subjectId
batchId
facultyId / eligible faculty relation
academicYear
semester
active
```

This is important because Person B needs to know which subjects actually belong to which student group.

If the existing project already models this relationship, reuse it.

---

# 15. ACADEMIC CALENDAR

Create a clean shared academic-calendar data model.

This data will later be used by the Faculty-side lesson-plan module, so do not make it timetable-specific.

Store at minimum:

```text
calendarId
academicYear
semester
startDate
endDate
workingDays
saturdayRule
sundayRule
holidays
```

For holidays, store structured records:

```text
holidayId
date
name
type
description
```

Holiday types:

```text
GOVERNMENT
COLLEGE
OTHER
```

Support:

- Government holidays
- College holidays
- Semester start/end
- Working days
- Saturday rule
- Sunday rule

Examples of Saturday rules:

```text
ALL_WORKING
ALL_HOLIDAY
ALTERNATE
CUSTOM
```

Do not hardcode a particular college's holiday calendar.

---

# 16. WORKING DAYS AND TIMESLOTS

This is an important data requirement for the timetable engine.

The scheduler cannot generate a timetable unless it knows the available time slots.

Create a configurable availability/timeslot structure such as:

```text
timeslotId
dayOfWeek
startTime
endTime
periodNumber
active
```

The available slots must be derived/filtered using the academic calendar rules and holidays.

Example:

```text
Monday 09:00–10:00
Monday 10:00–11:00
Monday 11:15–12:15
...
```

Do NOT build the actual scheduling algorithm.

Person A provides the available slots.

Person B decides where classes are placed.

---

# 17. DATA RELATIONSHIPS

The data should logically connect as:

```text
Academic Calendar
       ↓
Available Working Days / Timeslots

Department
       ↓
Semester
       ↓
Batch / Section
       ↓
Subject Offering
       ↓
Subject
       ↓
Credits
       ↓
NEP Rule
       ↓
Required Weekly Hours

Faculty ←→ Subject
Faculty → Maximum Weekly Load

Room
  ├── Lecture Hall
  └── Lab
```

---

# 18. DATA CONTRACT WITH PERSON B

This is extremely important.

Create/document one clear scheduler input contract.

Person B's scheduler should receive data conceptually like:

```json
{
  "subjects": [
    {
      "subjectId": "SUB001",
      "subjectCode": "CS101",
      "subjectName": "Data Structures",
      "batchId": "B1",
      "theoryCredits": 3,
      "labCredits": 1,
      "tutorialCredits": 0,
      "theoryHoursPerWeek": 3,
      "labHoursPerWeek": 2,
      "tutorialHoursPerWeek": 0,
      "totalWeeklyHours": 5,
      "requiresLab": true
    }
  ],
  "faculty": [
    {
      "facultyId": "F001",
      "name": "Faculty Name",
      "subjectIds": ["SUB001"],
      "maxWeeklyLoad": 16
    }
  ],
  "rooms": [
    {
      "roomId": "R001",
      "name": "Lab 1",
      "roomType": "LAB",
      "capacity": 60
    }
  ],
  "batches": [
    {
      "batchId": "B1",
      "name": "CSE-A",
      "semester": 5,
      "strength": 60
    }
  ],
  "timeslots": [
    {
      "timeslotId": "TS001",
      "day": "MONDAY",
      "startTime": "09:00",
      "endTime": "10:00"
    }
  ]
}
```

This is an example contract, not necessarily the exact final schema.

### Important:

Before implementing deep logic, inspect the existing project and coordinate the final field names/types with Person B.

The contract must be stable.

Do not make Person B reverse-engineer the frontend/database.

---

# 19. DATA READINESS / VALIDATION

Create a **Data Readiness Check** for the admin.

Before allowing timetable generation, check for missing/invalid data.

Examples:

### Subjects
- Missing subject code
- Missing credits
- Invalid credits
- Missing weekly-hour calculation
- Invalid lab configuration

### Faculty
- Faculty without subjects
- Missing max load
- Invalid subject mappings

### Rooms
- No rooms
- Invalid capacity
- Missing lab rooms when lab subjects exist

### Batches
- Missing batch/section
- Invalid strength

### Calendar
- No working days
- No available timeslots
- Semester dates invalid

### NEP rules
- No active rule
- Missing conversion values

### Offerings
- Batch has no subjects
- Subject has no batch offering
- Subject offering has no faculty eligibility where required

Display actionable messages such as:

```text
Cannot generate timetable.

3 issues found:
1. CSE-A has no subjects assigned.
2. Java Lab requires a LAB room, but no active LAB room exists.
3. No active credit-to-hours rule is configured.
```

Do not implement the scheduler itself.

---

# 20. GENERATE TIMETABLE BUTTON

The Admin UI may contain:

```text
Generate Timetable
```

But the button must call/use a clearly separated scheduler service/API boundary.

If Person B's scheduler is not yet integrated:

- Create a clean placeholder/service interface.
- Do not fake timetable results.
- Do not implement a duplicate scheduling algorithm.
- Show a clear state such as:
  `Scheduler service not connected yet. Data validation passed.`

Once Person B integrates the engine, the same boundary should be usable without rewriting the admin data layer.

---

# 21. UI REQUIREMENTS

The UI should be:

- Clean
- Simple
- Responsive
- Consistent with the existing Odysseus design
- Easy for a college administrator to understand

For every form:

- Labels must be clear.
- Required fields must be marked.
- Validation errors should appear near the relevant field.
- Avoid technical database terminology where unnecessary.
- Use dropdowns for relationships instead of free-text IDs.
- Use date pickers for dates.
- Use time pickers for times.
- Show calculated weekly hours prominently.

---

# 22. LIVE CREDIT CALCULATION EXAMPLE

If the configured rule is:

```text
Theory: 1 credit → 1 hr/week
Lab:    1 credit → 2 hr/week
Tutorial: 1 credit → 1 hr/week
```

And admin enters:

```text
Theory = 3
Lab = 1
Tutorial = 0
```

Display:

```text
Theory Hours / Week:    3
Lab Hours / Week:       2
Tutorial Hours / Week:  0
--------------------------------
Total Hours / Week:     5
```

If the admin changes Lab Credits from `1` to `2`, immediately update:

```text
Lab Hours / Week: 4
Total Hours / Week: 7
```

Again: these conversion values are configurable and are only an example.

---

# 23. PERSISTENCE

Do not rely only on temporary frontend state.

Use the existing project database/backend if available.

All master data must survive page refresh/restart.

If the existing project currently uses local storage or another temporary mechanism, inspect it first and follow the project's architecture unless there is a strong reason to introduce a backend.

Do not introduce a completely different database technology without necessity.

---

# 24. CRUD SAFETY

When deleting data:

- Warn the admin if the record is referenced elsewhere.
- Do not silently break relationships.
- Prefer deactivation (`active = false`) where appropriate.
- Prevent deletion of records required by existing offerings/relationships unless dependencies are handled safely.

Example:

If a faculty member is assigned to active subject offerings, do not blindly delete the faculty record.

---

# 25. NO HARDCODED COLLEGE DATA

Do not hardcode:

- Faculty names
- Subject names
- Rooms
- Holidays
- Departments
- Semesters
- Credit conversion rules
- Working days
- Timeslots

These must be admin-configurable data.

Demo/seed data may be provided only if the existing project already uses seed data, and it must be clearly distinguishable from real data.

---

# 26. FACULTY-SIDE SHARED CALENDAR

The academic calendar must remain reusable by the future Faculty lesson-plan module.

Therefore:

- Keep calendar data independent of timetable-generation code.
- Do not store lesson-plan-specific information inside timetable records.
- Expose calendar/working-day information through a reusable service/API/model.

---

# 27. ERROR HANDLING

Implement useful error handling.

Examples:

```text
Invalid credit value.
Theory credits cannot be negative.
Please select a department.
This subject code already exists.
No active NEP rule is available for this academic context.
Cannot deactivate this room because it is used by an active offering.
```

Do not expose raw stack traces or database errors to normal admin users.

---

# 28. TESTING REQUIREMENTS

After implementation, test at minimum:

### Login
- Correct admin login
- Wrong password
- Faculty login
- Faculty attempting admin route
- Logout

### Subject
- Create subject
- Edit subject
- Delete/deactivate subject
- Duplicate subject code
- Invalid credit
- Live hour calculation

### NEP Rules
- Create rule
- Edit rule
- Activate/deactivate rule
- Change conversion values
- Verify subject hours update

### Faculty
- Create faculty
- Assign teachable subjects
- Set maximum workload
- Invalid duplicate employee ID

### Room
- Create lecture room
- Create lab
- Invalid capacity
- Duplicate room

### Batch
- Create batch
- Invalid strength
- Duplicate batch

### Calendar
- Add working day
- Add Saturday/Sunday rule
- Add government holiday
- Add college holiday
- Create timeslots
- Prevent invalid date/time configuration

### Readiness
- Verify incomplete data is detected.
- Verify valid data passes readiness checks.

---

# 29. ACCEPTANCE CRITERIA

Person A's work is complete only when:

- [ ] Admin can log in.
- [ ] Role-based access works.
- [ ] Admin dashboard exists.
- [ ] Subjects can be managed.
- [ ] Theory/lab/tutorial credits can be entered.
- [ ] Weekly hours calculate automatically.
- [ ] Credit-to-hour conversion is configurable.
- [ ] Faculty can be managed.
- [ ] Faculty teaching eligibility can be managed.
- [ ] Faculty maximum weekly load is stored.
- [ ] Rooms can be managed.
- [ ] Lecture halls and labs are distinguished.
- [ ] Batch/section data can be managed.
- [ ] Subjects can be associated with batches.
- [ ] Academic calendar can be managed.
- [ ] Government and college holidays can be stored.
- [ ] Saturday/Sunday rules can be configured.
- [ ] Working timeslots are available as scheduler input.
- [ ] Data persists.
- [ ] Validation prevents invalid data.
- [ ] Data readiness check works.
- [ ] Scheduler input contract is documented.
- [ ] Generate button has a clean integration boundary.
- [ ] No duplicate scheduling algorithm has been created.
- [ ] Person B's OptaPlanner code has not been unnecessarily modified.
- [ ] Existing project functionality has not been broken.

---

# 30. IMPORTANT IMPLEMENTATION RULES FOR ANTIGRAVITY

### DO

- Inspect first.
- Reuse existing architecture.
- Keep modules separated.
- Make credit rules configurable.
- Keep data normalized and relational where appropriate.
- Validate data before it reaches the scheduler.
- Keep the scheduler integration interface clean.
- Document important schema/API decisions.
- Test the complete admin data-entry workflow.

### DO NOT

- Do not blindly rewrite the whole project.
- Do not delete existing working features.
- Do not create duplicate files/components for existing functionality.
- Do not implement OptaPlanner scheduling.
- Do not invent official NEP conversion numbers.
- Do not hardcode college holidays or timetable rules.
- Do not fake generated timetables.
- Do not bypass role-based security.
- Do not store passwords as plain text.
- Do not make Person B dependent on frontend-only state.

---

# 31. FINAL DELIVERABLES

At the end, Person A should have:

### Frontend
- Login
- Admin dashboard
- Subject management
- Faculty management
- Room management
- Batch/section management
- Subject offering management
- Academic calendar management
- Timeslot management
- NEP rule management
- Live credit/hour calculation
- Data readiness screen
- Generate timetable integration point

### Data layer
- Models/entities/schema
- Relationships
- CRUD operations
- Validation
- Persistence
- Calendar data
- NEP rules
- Scheduler-ready data contract

### Documentation
Create/update a short developer document explaining:

1. Data model
2. Important relationships
3. Credit-to-hours calculation
4. API/service endpoints if applicable
5. Scheduler input contract
6. How Person B should consume the data
7. What Person A owns vs what Person B owns

---

# 32. FINAL INSTRUCTION

**Do not stop at creating UI mockups.**

Implement the complete Person A data/input workflow using the existing Odysseus architecture.

The desired end-to-end flow is:

```text
ADMIN LOGIN
    ↓
ADMIN DASHBOARD
    ↓
Configure NEP Credit Rules
    ↓
Enter Subjects + Credits
    ↓
LIVE WEEKLY-HOUR CALCULATION
    ↓
Enter Faculty + Teaching Eligibility + Max Load
    ↓
Enter Rooms
    ↓
Enter Batches / Sections
    ↓
Assign Subjects to Batches
    ↓
Configure Academic Calendar
    ↓
Configure Working Days + Timeslots
    ↓
DATA READINESS CHECK
    ↓
VALIDATED SCHEDULER INPUT
    ↓
HAND OFF TO PERSON B
    ↓
GENERATE TIMETABLE
```

The key principle is:

> **Person A owns what goes INTO the timetable generator. Person B owns how the timetable is GENERATED.**

Keep that boundary clean.
