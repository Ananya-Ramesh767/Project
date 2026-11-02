# Lesson Plan Module — Source Material (Person C: Catherine)

This is reference material gathered from the team's actual course documents for the
"Easwari Engineering College Timetable Scheduler and Lesson Planner" project (OOPS-JAVA---Odysseus repo). It grounds the
lesson-plan module in real data instead of invented numbers. Do not invent
institution-specific numbers (holiday dates, credit rules, etc.) beyond what is here —
treat everything below as authoritative source-of-truth extracted from the team's own
PDFs/PPTX/DOCX.

## 1. Project identity & team boundary

- Repo: OOPS-JAVA---Odysseus (formerly "Odysseus"). Real code lives on `features/admin-data-inputs`
  (the `main` branch is just the initial empty scaffold).
- Team 14, problem statement: "Smart Classroom - AI enabled Timetable Scheduler,
  Lesson plan preparation" — a constraint-based, forecast-aware scheduling engine
  for flexible credit-based curricula (NEP 2020).
- Three-way split documented in `SCHEDULER_CONTRACT.md` and
  `Easwari_Engineering_College_Person_A_Admin_Data_Layer_Final.md` (both at repo root):
  - **Person A (Ananya)** — admin/data layer: Subject/Faculty/Room/Batch masters,
    SubjectOffering, AcademicCalendar + CalendarHoliday, CollegeTimeslot, NepCreditRule,
    auth (JWT), data-readiness checks. **DONE** — do not re-implement or break this.
  - **Person B (Anish)** — OptaPlanner-based timetable-generation engine (constraint
    solver). Out of scope here — do not touch/duplicate.
  - **Person C (Catherine — this task)** — the **faculty-side Lesson Plan module**.
    Explicitly called out as *not yet built* in Person A's doc (section 4: "Have the
    foundation for a future faculty dashboard/lesson-plan module. Do not build the
    complete Faculty lesson-plan module unless it already exists" — it does not exist
    yet) and section 26 ("The academic calendar must remain reusable by the future
    Faculty lesson-plan module... Expose calendar/working-day information through a
    reusable service/API/model").
- From the team's problem-statement deck (slide 8, "LESSON PLAN"), the intended shape
  of this feature is explicitly:
  - "A syllabus-sequence field per course in the database"
  - "A soft constraint ... penalize if dependent topics are scheduled out of order"
  - i.e. each subject has an ordered sequence of syllabus units/topics, and the lesson
    plan must respect that order across the semester's working days.

## 2. Existing backend schema (already implemented — reuse, don't duplicate)

Located at `backend/src/main/java/com/easwari/backend/domain/` on
`features/admin-data-inputs` (already merged into this branch's history):

- **Subject**: `id, subjectCode, subjectName, department, semester, subjectType`
  (`THEORY | LAB | TUTORIAL | THEORY_AND_LAB`), `theoryCredits, labCredits,
  tutorialCredits, requiresLab, labDuration (Integer, continuous periods),
  calculatedWeeklyHours (double), active`.
- **SubjectOffering**: `id, batch (→Batch), subject (→Subject),
  assignedFaculty (→Faculty), academicYear, semester, active`.
- **Batch**: `id, batchName, section, department, semester, strength, academicYear, active`.
- **Faculty**: `id, employeeId, name, email, department, maxWeeklyLoad, active,
  eligibleSubjects (Set<Subject>, many-to-many)`.
- **AcademicCalendar**: `id, academicYear, semester, startDate, endDate,
  SaturdayRule, SundayRule (ALL_HOLIDAY|ALL_WORKING|ALTERNATE|CUSTOM),
  active, holidays (List<CalendarHoliday>, one-to-many)`.
- **CalendarHoliday**: `id, holidayDate (LocalDate), name, type
  (GOVERNMENT|COLLEGE|OTHER), description, calendar (→AcademicCalendar)`.
- **CollegeTimeslot**: `id, dayOfWeek (MONDAY..FRIDAY), periodNumber (1..8, 0 for
  break/lunch), periodLabel (P1..P8, BREAK, LUNCH), startTime, endTime, isBreak,
  isLunch, active`. Standard day per `SCHEDULER_CONTRACT.md`: P1 08:15–09:05,
  P2 09:05–09:55, BREAK 09:55–10:10, P3 10:10–11:00, P4 11:00–11:50, P5 11:50–12:40,
  LUNCH 12:40–13:30, P6 13:30–14:15, P7 14:15–15:00, P8 15:00–15:45.
- **NepCreditRule**: `id, ruleName, academicYear, program, department,
  theoryHoursPerCredit, labHoursPerCredit, tutorialHoursPerCredit, active`.
- There is **no** existing entity for syllabus units, course outcomes/objectives, or
  lesson-plan records — that is the gap this module fills.
- Existing controllers/services follow a plain Spring Boot MVC pattern: `domain` →
  `repository` (Spring Data JPA) → `service` → `controller` (`@RestController`),
  DTOs under `dto/`. Frontend is React + Vite, pages under
  `frontend/src/pages/*Page.jsx`, shared UI bits under `frontend/src/components/`,
  routing/dashboard in `App.jsx` / `Sidebar.jsx` / `AdminDashboard.jsx`. Auth uses a
  JWT filter (`JwtAuthenticationFilter`, `JwtTokenProvider`) with a `ProtectedRoute.jsx`
  on the frontend — the lesson-plan pages must plug into the same auth/role pattern
  (ADMIN can manage everything; FACULTY role already exists per Person A's doc section
  4, currently just a login stub — this module is what finally gives it something to do).

## 3. Anna University Academic Regulations 2025 — rules that constrain the lesson plan

(Source: `ACADEMIC REGULATIONS 2025).pdf`, Anna University, applicable to affiliated
non-autonomous institutions incl. this college, from AY2025-26.)

- **Semester** = minimum 90 working days including exams, ~8 contact hours/day
  (Clause 3.5). Odd semesters (I, III, V, VII) run July/Aug–Nov/Dec.
- **Class timings**: 08:30 AM – 05:00 PM with recesses/lunch, Monday–Friday only for
  regular classes (Clause 9) — matches the `CollegeTimeslot` P1–P8 grid above.
- **Credit definition** (Clause 5.5, Table 1): 1 Lecture hour = 1 credit,
  1 Tutorial hour = 1 credit, 1 Laboratory hour = course-specific (per curriculum) —
  this is exactly what `NepCreditRule` already encodes as configurable data; do not
  hardcode a different ratio.
- **Course types & their L-T-P-C periods/week** appear in the curriculum document
  (see §4 below) as a `Course Code / Course Title / Semester / Category /
  Periods per Week (L, T, P, R) / C` header row — "R" is a project/research-hours
  column distinct from "P" (lab/practical), used by R23 regulation curricula.
- **Evaluation weightings relevant to lesson-plan pacing** (Table 3/4, Clause 17.4,
  "Laboratory Integrated Theory" courses): continuous assessment activities are
  built around two Internal Examinations (IEs) roughly at mid-term and end-of-term —
  a lesson plan generator should be able to mark IE checkpoints against the
  day-by-day plan, though this is not a hard requirement for a first version.
- No specific national/state holiday list is published in this regulation document
  (holidays are institution-specific, see §5 — do NOT hardcode a generic Indian
  holiday list; only use what the college's own schedule specifies).

## 4. Curriculum & Syllabus (CSE, R23 regulation, V1.2, Semester III)

(Source: `CSE_AY26-27_R23_V1.2_Sem-3_Curriculum&Syllabus.docx`.) Every course entry in
this document follows one fixed template — this is the "FAIML document" structure the
task refers to. Two Semester-III courses are fully documented in the file; use BOTH as
the canonical shape for the new `SyllabusUnit`/`CourseOutcome` data model (the table
header is literally `Course Code | Course Title | Semester | Category |
Periods/Week (L, T, P, R) | C`):

### 4.1 Course used as the LTPC/CO/Unit template — Fundamentals of AI & ML ("FAIML")
- Course Code: `2321CSC303J` — FUNDAMENTALS OF ARTIFICIAL INTELLIGENCE AND MACHINE
  LEARNING — Semester III — Category **PC** (Programme Core)
- Periods/Week: **L=2, T=0, P=2, R=0** → **C=3**. Course Type: THEORY CUM PRACTICAL
  (this is Person A's `subjectType = THEORY_AND_LAB`, `requiresLab = true`).
- Course Objectives (5, unnumbered, prefixed "To ..."): introduce foundational AI
  concepts/history/applications; understand knowledge representation & reasoning;
  introduce ML concepts/types/algorithms; supervised learning for classification &
  regression; unsupervised learning for clustering & dimensionality reduction.
- Course Outcomes (numbered CO1–CO5 — this numbering scheme is what "into LTPR with a
  number" refers to: every course's outcomes are numbered COn, and every unit's
  periods are counted against the L/P period totals):
  - CO1 Implement AI in problem solving techniques.
  - CO2 Interpret knowledge representation & reasoning in AI systems.
  - CO3 Analyse ML algorithms (linear/logistic regression).
  - CO4 Implement & evaluate supervised learning algorithms (classification/regression).
  - CO5 Apply unsupervised learning/clustering to unlabeled data.
- Course Content — 5 theory units, **6 periods each = 30 total theory periods**:
  1. Introduction to AI (agents, problem solving, search strategies, CSPs)
  2. Knowledge Representation & Reasoning (game search, logical agents, neural nets,
     Bayes/Bayesian networks)
  3. Introduction to Machine Learning (supervised learning, classification/regression,
     over/underfitting)
  4. Unsupervised Learning (types, preprocessing, dimensionality reduction, clustering)
  5. Applied ML & Evaluation (feature selection, cross-validation, grid search, metrics)
- Lab — 6 experiments, **30 total practical periods** (adversarial-search game agent,
  logical agent w/ resolution, Bayesian network inference, supervised-model comparison
  on a dataset, K-Means/Agglomerative clustering, PCA).
- This is a textbook example of a **Laboratory Integrated Theory (LIT)** course per the
  Academic Regulations: theory units and lab experiments run in parallel across the
  same weeks, each with their own period count, both driven by the same 5-unit syllabus
  sequence.

### 4.2 The team's own course — Object Oriented Programming using Java
- Course Code: `2321CSC304R` — OBJECT ORIENTED PROGRAMMING USING JAVA — Semester III —
  Category **PC**.
- Periods/Week: **L=2, T=0, P=0, R=4** → **C=4**. Course Type: THEORY CUM PROJECT
  (note: R≠0 with P=0 — a mini-project track instead of a weekly lab; the lesson-plan
  data model must support R as its own period type, distinct from P).
- Course Outcomes CO1–CO5: apply OOP principles in Java; execute Java programs with
  constructs/methods/IO; implement polymorphism/overloading/overriding/inheritance/
  interfaces; handle exceptions & file operations; build multithreaded apps using Java
  Collections.
- Course Content — 5 theory units, 6 periods each = 30 total:
  1. Introduction to OOP & Java Basics (OOP fundamentals, JVM/JDK/JRE, syntax, data
     types, control structures, I/O)
  2. Methods and String Handling (classes/objects, overloading, constructors, static/
     final, GC, Strings/StringBuffer/StringBuilder, arrays/ArrayList)
  3. Inheritance and Polymorphism (types of inheritance, overriding, super, dynamic
     dispatch, abstract classes, interfaces, packages/access modifiers)
  4. Exception Handling, Files and Collections (checked/unchecked exceptions, custom
     exceptions, wrapper classes, I/O streams, Collections Framework)
  5. Threads, Generics and JDBC (multithreading, thread lifecycle, synchronization,
     generics, JDBC)
- Plus a semester-long individual/group Project (Total Periods = 60) instead of a lab.
- **This is literally the course this Java project itself belongs to** — worth using
  as a live demo dataset when testing the lesson-plan generator end-to-end.

## 5. Academic Schedule — Odd Semester (2026–2027), III Semester, Regulations 2023 V1.2

(Source: `Academic schedule UG - 3rd sem -2026-2027.pdf`, Easwari Engineering College —
an autonomous institution, Anna University-affiliated, dated 25.04.2026. OCR text is
somewhat noisy; dates below are read from the table and cross-checked against the day
counters printed alongside the table, e.g. "17 Days / 32 Days / 38 Days / 52 Days /
57 Days / 68 Days / 79 Days" which are the running cumulative working-day counts at
each successive milestone through the semester.)

- Class Commencement: **06.07.2026** (Monday)
- Unit Test – I: **22.07.2026 – 24.07.2026**
- CAT‑1: **10.08.2026 – 18.08.2026** (CAT‑1 Report submission 21.08.2026; retest dates
  22.08.2026 and 29.08.2026)
- Group Presentation: **24.08.2026 – 27.08.2026**
- CCC Training: **03.09.2026 – 14.09.2026** (planned window 22.09–25.09 per one note —
  treat the table's own date range as authoritative over the footnote if they conflict)
- Unit Test – II: **21.09.2026 – 25.09.2026**
- CAT‑2: **05.10.2026 – 13.10.2026** (CAT‑2 Report submission 16.10.2026; retest dates
  24.10.2026 and 31.10.2026)
- Last Working Day: **30.10.2026**
- End Semester Practical Exams: **02.11.2026 – 06.11.2026**
- End Semester Theory Exams: start **11.11.2026**
- Other administrative dates (not teaching days, but useful if the module ever needs
  them): Feedback‑1 submission 03.08.2026, Feedback‑2 submission 30.09.2026,
  Attendance Report‑1 21.08.2026, Attendance Report‑2 15.10.2026, Attendance proforma
  31.10.2026, Internal Marks Report to Academic Office 30.10.2026 / to COE 05.11.2026.
- **No explicit list of individual government/festival holiday dates is printed in
  this schedule PDF** — only the block above. This means: (a) the `CalendarHoliday`
  entity Person A already built is the correct place for an admin to enter the actual
  gazetted holidays (Pongal, Independence Day, Diwali, etc. — none of which are
  invented here since they're not in the source document), and (b) the lesson-plan
  generator's working-day calculator must treat `AcademicCalendar.startDate/endDate`
  (06.07.2026–30.10.2026 for this semester), `SaturdayRule`/`SundayRule`, and
  `CalendarHoliday` rows as the sole source of truth for which calendar dates are
  teaching days — never a hardcoded list.
- Total semester span 06.07.2026–30.10.2026 is ~17 weeks; after removing Sundays,
  Saturdays (per rule), the above non-teaching blocks (unit tests/CATs still count as
  teaching-adjacent but Group Presentation/CCC Training weeks reduce normal
  period availability), and whatever holidays the admin enters, roughly 90+ actual
  teaching days remain — consistent with the Regulation's 90-working-day minimum.

## 6. What "into LTPR with a number" means for the data model

Putting §3 + §4 together, the lesson-plan module's syllabus data model must capture,
per `Subject`:
1. Its **L/T/P/R period counts** (already partly on `Subject` as credits — but the
   lesson-plan module needs *periods*, which the curriculum doc gives directly per
   course, e.g. FAIML = 2‑0‑2‑0, OOP Java = 2‑0‑0‑4).
2. An **ordered list of syllabus units** (Unit I..V, or however many), each with a
   title, its period allocation (e.g. 6 periods), and topic content — theory units
   count against L periods, lab experiment lists count against P periods, project
   milestones count against R periods.
3. **Numbered Course Outcomes** (CO1..COn) and Course Objectives, each of which can
   optionally be tagged to the unit(s) that deliver it — this "numbered CO" pattern is
   exactly what the curriculum document already does and what the lesson-plan feature
   should preserve so a generated day's lesson plan can show which CO/unit it maps to.

This is the shape Phase 1 should formalize into concrete entities/DTOs.
