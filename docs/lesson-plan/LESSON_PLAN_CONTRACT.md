# LESSON PLAN DATA CONTRACT & MODULE DESIGN

## Overview

This document specifies the exact JPA entities, algorithms, and REST API surface for
**Person C (Catherine)'s Faculty-side Lesson Plan module**, to be implemented
literally by the Phase 2 backend engineer. It is a sibling document to
`SCHEDULER_CONTRACT.md` (Person A → Person B), written for the same audience and in
the same style, but for a module that does not run an optimizer — it is a
deterministic content-sequencing tool that turns a subject's syllabus into a
day-by-day teaching plan against the shared academic calendar.

- **Source of truth for facts used below**: `docs/lesson-plan/SOURCE_MATERIAL.md`.
  Every number, date, and text field in this document and in
  `seed-syllabus-data.json` traces back to a line in that file. Nothing here is
  invented; where the source gives only a track total instead of a per-item
  breakdown (the FAIML lab), that is called out explicitly in section 3.2 rather
  than silently presented as sourced fact.
- **Status**: Design only. No Java/React source files have been modified to produce
  this document. Phase 2 implements this contract verbatim.
- **Reused entities**: `Subject`, `SubjectOffering`, `Batch`, `Faculty`,
  `AcademicCalendar`, `CalendarHoliday`, `CollegeTimeslot` — all at
  `backend/src/main/java/com/easwari/backend/domain/`, all already implemented by
  Person A. This module adds new entities that reference them; it does not
  redesign or duplicate them.

---

## 0. Ownership boundary (restated for this module)

| Concern | Owner |
|---|---|
| Subject/Faculty/Batch/Offering masters, credits, NEP rules | Person A (done, untouched here) |
| Academic calendar, holidays, working-day rules, timeslot grid | Person A (done, reused here) |
| Constraint-solved, conflict-free timetable (rooms/faculty/periods across the whole college) | Person B (OptaPlanner, untouched, not duplicated here) |
| Per-subject syllabus (units, COs, objectives) + per-batch day-by-day lesson plan, faculty completion tracking | **Person C — this document** |

This module is explicitly **not** a second scheduling engine. It does not resolve
room/faculty/period conflicts across subjects the way Person B's solver does. It
answers a narrower question Person B's solver does not: *for one subject offering,
in what order and on what calendar dates should the syllabus units be taught,* given
the shared calendar's working days. Where a specific period-of-day is needed for a
generated session, this module assigns a fixed nominal slot per subject-offering
track (section 4.3) purely so the day-by-day plan is displayable — it is not a claim
that no other subject is also using that room/period. If Person B's solver output
later becomes available, the nominal slot on each `LessonPlanSession` can be
reconciled against the real timetable without changing the unit-sequencing logic.

---

## 1. New JPA Entities

### 1.1 Extending `Subject` (additive columns only)

**Decision: extend the existing `Subject` entity with four new nullable columns.
Do not create a companion "SubjectPeriodProfile" entity.**

Justification:
- The L/T/P/R weekly period counts are a 1:1, non-repeating, non-versioned
  attribute of a Subject — structurally identical in shape to the
  `theoryCredits`/`labCredits`/`tutorialCredits` triplet Subject already carries.
  A companion entity would just be a permanent 1:1 join for no relational benefit,
  and every future read of "how many lecture periods does this subject have" (the
  generation algorithm, the UI, reporting) would need an extra join.
- The task constraint is explicit: *adding new nullable columns to `Subject` is
  safe; renaming/removing existing fields is not.* All four new fields are
  `nullable` with no default that could conflict with existing rows, and no
  existing column is touched, renamed, or reinterpreted. `theoryCredits` /
  `labCredits` / `tutorialCredits` / `calculatedWeeklyHours` and
  `CreditCalculationService`'s pipeline are completely untouched — this module's
  new fields are periods/week (a curriculum-book fact), not credits (an NEP-rule
  computed fact), and the two must not be conflated or reconciled automatically.
- Under `spring.jpa.hibernate.ddl-auto: update` (see `application.yml`), adding
  nullable columns to an existing table is a non-destructive, automatic schema
  change — no manual migration is required, existing `subjects` rows simply get
  `NULL` in the new columns until an admin/CO-owner backfills them.

New fields on `Subject` (append to the existing class, do not reorder or remove
anything):

```java
// --- Lesson Plan module additions (Person C) ---
// Weekly period counts from the R23 curriculum's "Periods/Week (L,T,P,R)" column.
// Distinct from theoryCredits/labCredits/tutorialCredits above, which are NEP
// credit values consumed by CreditCalculationService. These are raw period counts
// as printed in the curriculum document and are NOT run through any credit rule.
private Integer weeklyLecturePeriods;   // L
private Integer weeklyTutorialPeriods;  // T
private Integer weeklyPracticalPeriods; // P (lab)
private Integer weeklyProjectPeriods;   // R (project/research — distinct from P;
                                         // e.g. OOP-Java has P=0, R=4)

// The curriculum document's own "C" (total credits) column, kept purely for
// display/traceability back to the curriculum table. NOT used by
// CreditCalculationService and NOT guaranteed to equal
// theoryCredits+labCredits+tutorialCredits (those are computed via NepCreditRule;
// this is the number printed in the syllabus document).
private Double curriculumCredits;
```

All four period fields and `curriculumCredits` are `nullable` (no `@Column(nullable
= false)`), so existing `Subject` rows created before this module ships remain
valid with no migration step.

### 1.2 `SyllabusUnit`

One ordered content block within one delivery track (`THEORY`, `LAB`, or
`PROJECT`) of one Subject. FAIML has two independent, parallel tracks (5 THEORY
units of 6 periods each; 6 LAB units of periods each); OOP-Java has a THEORY track
(5 units of 6 periods) and a single-entry PROJECT track (one continuous 60-period
block, per the explicit instruction that Project is *not* split into discrete
units).

```java
package com.easwari.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "syllabus_units")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyllabusUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(nullable = false)
    private String sessionType; // THEORY | LAB | PROJECT (mirrors Subject.subjectType vocabulary)

    @Column(nullable = false)
    private Integer unitNumber; // 1-based ordering WITHIN (subject, sessionType).
                                 // Never reorder/skip — this is the sequence the
                                 // generation algorithm walks strictly in order.

    @Column(nullable = false)
    private String title;

    @ElementCollection
    @CollectionTable(name = "syllabus_unit_topics", joinColumns = @JoinColumn(name = "unit_id"))
    @OrderColumn(name = "topic_order")
    @Column(name = "topic", length = 500)
    @Builder.Default
    private List<String> topics = new ArrayList<>();

    @Column(nullable = false)
    private Integer periodsAllotted; // consumed by the generation algorithm (section 3)

    @Builder.Default
    private boolean periodsAllottedIsDerived = false;
    // true when the source document gave only a track TOTAL (not a per-unit
    // figure) and this value was computed by an even split — see section 3.2.
    // Kept as a persisted flag, not just a doc comment, so the UI/audit trail can
    // visibly flag "this period count was computed, not printed in the syllabus."

    private String derivationNote; // human-readable explanation when the flag above is true; null otherwise

    @ManyToMany
    @JoinTable(
        name = "syllabus_unit_course_outcomes",
        joinColumns = @JoinColumn(name = "unit_id"),
        inverseJoinColumns = @JoinColumn(name = "course_outcome_id")
    )
    @Builder.Default
    private java.util.Set<CourseOutcome> coveredOutcomes = new java.util.HashSet<>();
    // Optional. SOURCE_MATERIAL.md does not state an explicit unit->CO mapping for
    // either course, so seed-syllabus-data.json leaves this empty; populate later
    // via the CO/unit CRUD endpoints once a subject owner enters it.

    @Builder.Default
    private boolean active = true;
}
```

Unique constraint (enforce in repository/service, not just convention):
`(subject_id, sessionType, unitNumber)` must be unique among active rows.

### 1.3 `CourseOutcome`

```java
package com.easwari.backend.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "course_outcomes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseOutcome {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(nullable = false)
    private Integer coNumber; // 1..N, matches the curriculum doc's "CO1..CO5" numbering exactly

    @Column(nullable = false, length = 1000)
    private String description;

    @Builder.Default
    private boolean active = true;
}
```

### 1.4 `CourseObjective`

```java
package com.easwari.backend.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "course_objectives")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseObjective {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(nullable = false)
    private Integer sequenceNumber; // 1-based; the curriculum doc's objectives are
                                     // unnumbered ("To ..." bullets) — sequence is
                                     // for stable ordering/display only, not a CO-style code

    @Column(nullable = false, length = 1000)
    private String description;

    @Builder.Default
    private boolean active = true;
}
```

### 1.5 `LessonPlan` (header)

One row per `(SubjectOffering, AcademicCalendar)` — one subject's plan for one
batch for one semester.

```java
package com.easwari.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "lesson_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "subject_offering_id", nullable = false)
    private SubjectOffering subjectOffering; // reused unchanged: links batch+subject+faculty

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "academic_calendar_id", nullable = false)
    private AcademicCalendar academicCalendar; // reused unchanged: working-day source of truth

    private LocalDateTime generatedAt;

    @Column(length = 2000)
    private String generationNotes; // e.g. "THEORY: Mon/Wed; PROJECT: Tue/Thu (2p);
                                     // Unit Test I (22-24 Jul) not auto-excluded, see contract 3.3"

    @Builder.Default
    private boolean active = true;
}
```

Uniqueness: `(subject_offering_id, academic_calendar_id)` unique among active rows
— one live plan per offering per calendar. Regeneration (section 4, `force=true`)
deactivates the old row rather than deleting it, matching the existing codebase's
soft-delete convention.

### 1.6 `LessonPlanSession` (child — one row per calendar date + period-type occurrence)

```java
package com.easwari.backend.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "lesson_plan_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonPlanSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lesson_plan_id", nullable = false)
    private LessonPlan lessonPlan;

    @Column(nullable = false)
    private LocalDate sessionDate;

    @Column(nullable = false)
    private String dayOfWeek; // denormalized from sessionDate, same vocabulary as CollegeTimeslot.dayOfWeek

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "start_timeslot_id")
    private CollegeTimeslot startTimeslot; // reused unchanged: the period-timing grid; nominal slot, see section 0

    @Column(nullable = false)
    private Integer periodsCount; // contiguous periods this occurrence spans (1 for a normal
                                   // theory period, Subject.labDuration for a lab block, etc.)

    @Column(nullable = false)
    private String periodType; // THEORY | LAB | PROJECT (matches SyllabusUnit.sessionType)

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "syllabus_unit_id", nullable = false)
    private SyllabusUnit syllabusUnit; // which unit this occurrence covers

    @Column(nullable = false)
    private Integer overallSequence; // 1-based, monotonically increasing across the WHOLE
                                      // LessonPlan in date order — gives a stable "session #N" for display

    @Column(nullable = false)
    @Builder.Default
    private String status = "PLANNED"; // PLANNED | COMPLETED | RESCHEDULED

    @Column(length = 1000)
    private String remarks; // faculty-editable free text (e.g. reschedule reason, actual topics covered)

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "rescheduled_to_session_id")
    private LessonPlanSession rescheduledTo; // self-reference; set when status=RESCHEDULED,
                                              // points at the new session that replaces this one.
                                              // Original row is kept (status=RESCHEDULED), never deleted,
                                              // so the plan's history stays auditable.

    @Builder.Default
    private boolean active = true;
}
```

### 1.7 Summary of what is reused unchanged

| Existing entity | Role in this module |
|---|---|
| `SubjectOffering` | Identifies which (batch, subject, faculty) a `LessonPlan` is for. Not modified. |
| `AcademicCalendar` + `CalendarHoliday` | Sole source of truth for which calendar dates are teaching days (section 2). Not modified, not duplicated into lesson-plan records — per Person A doc section 26. |
| `CollegeTimeslot` | Supplies the period-timing grid a `LessonPlanSession.startTimeslot` points into. Not modified. |
| `Subject` | Extended additively (section 1.1) — existing fields untouched. |

---

## 2. Working-day calculation algorithm

Input: one `AcademicCalendar` (with its `holidays` list already loaded).
Output: ordered `List<LocalDate>` of actual teaching dates between `startDate` and
`endDate` inclusive.

```text
function computeTeachingDates(calendar):
    holidaySet = { h.holidayDate for h in calendar.holidays }
    teachingDates = []
    date = calendar.startDate
    while date <= calendar.endDate:
        dow = date.dayOfWeek()
        nonTeaching = false

        if dow == SATURDAY:
            nonTeaching = resolveSaturdayRule(calendar.SaturdayRule, date)
        else if dow == SUNDAY:
            nonTeaching = resolveSundayRule(calendar.SundayRule, date)
        // Monday..Friday: teaching by default (Academic Regulations 2025, Clause 9:
        // "Monday-Friday only for regular classes" — Saturday is the only day whose
        // status depends on a configurable rule; Mon-Fri are never auto-excluded here)

        if not nonTeaching and date in holidaySet:
            nonTeaching = true   // CalendarHoliday always wins regardless of weekday

        if not nonTeaching:
            teachingDates.add(date)

        date = date.plusDays(1)

    return teachingDates


function resolveSaturdayRule(rule, date):
    switch rule:
        case "ALL_WORKING": return false          // every Saturday is a teaching day
        case "ALL_HOLIDAY": return true            // every Saturday is non-teaching
        case "ALTERNATE":
            // Explicit Phase-1 convention (NOT defined anywhere in
            // AcademicCalendar's schema or SOURCE_MATERIAL.md — the enum value
            // exists on AcademicCalendar.SaturdayRule but its semantics were never
            // specified by Person A or the regulations doc). This module adopts
            // the common Indian-institution convention: 2nd and 4th Saturday of
            // the month are holidays, 1st/3rd/5th are working. If Person A's
            // implementation later defines ALTERNATE differently, this function
            // must be updated to match — do not let two different definitions of
            // ALTERNATE exist in the codebase.
            n = ordinalSaturdayOfMonth(date)  // 1..5
            return (n == 2 or n == 4)
        case "CUSTOM":
            // Rule says "admin decides case by case" — with no separate
            // rule-detail field on AcademicCalendar, CUSTOM is implemented as
            // "every Saturday is a working day UNLESS it also appears as a row in
            // CalendarHoliday" (i.e. CUSTOM delegates entirely to the holiday
            // list). This keeps CUSTOM meaningfully different from ALL_WORKING
            // without inventing a new schema field.
            return false
        default:
            return true // fail safe: unrecognized rule -> treat Saturday as non-teaching


function resolveSundayRule(rule, date):
    // Same three cases as Saturday. SOURCE_MATERIAL.md and DataInitializer's seed
    // both only ever use "ALL_HOLIDAY" for Sunday; ALTERNATE/CUSTOM are supported
    // for schema completeness but not expected in practice.
    switch rule:
        case "ALL_WORKING": return false
        case "ALL_HOLIDAY": return true
        case "ALTERNATE": n = ordinalSundayOfMonth(date); return (n == 2 or n == 4)
        case "CUSTOM": return false
        default: return true
```

### 2.1 Known limitation — exam/assessment blocks are not auto-excluded

Per `SOURCE_MATERIAL.md` section 5, `AcademicCalendar.startDate/endDate`,
`SaturdayRule`/`SundayRule`, and `CalendarHoliday` are stated to be **the sole
source of truth** for teaching dates — never a hardcoded list. Unit Tests, CATs,
and Group Presentation windows from the academic schedule (e.g. Unit Test–I,
22–24 Jul 2026) are **not** holidays and are therefore **not** automatically
excluded by `computeTeachingDates`; they remain ordinary teaching dates unless an
admin has separately entered them as `CalendarHoliday` rows (type `OTHER`). This is
a deliberate v1 simplification, stated explicitly per the task's own allowance for
"a reasonable simplifying rule... but must be stated explicitly." See the worked
example in section 5.2, where 22–24 Jul falls inside Unit Test–I and the generator
still places sessions there. A Phase-2-and-beyond improvement would add an
`ExamBlock` concept the working-day calculator also consults — out of scope here.

---

## 3. Weekly period → weekday assignment rule

The existing schema does not assign specific weekdays to specific subjects — that
happens only inside Person B's solver, which this module must not duplicate.
Section 2 gives *which dates are teaching days at all*; this section gives the
Phase-1 rule for *which of those teaching days this particular subject-offering's
THEORY/LAB/PROJECT track meets on*, so that section 4's sequencing algorithm has
something concrete to walk.

### 3.1 Rule

For each `SubjectOffering`, for each track present on its `Subject`
(`weeklyLecturePeriods` > 0 → THEORY track exists; `weeklyPracticalPeriods` > 0 and
`requiresLab` → LAB track exists; `weeklyProjectPeriods` > 0 → PROJECT track
exists; `weeklyTutorialPeriods` > 0 → TUTORIAL track exists, handled identically to
THEORY):

```text
availableWeekdays = [MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY]
                     // + SATURDAY only if calendar.SaturdayRule ever resolves a
                     // given Saturday as teaching for at least one week; for the
                     // two example courses here, Saturday is not used (Academic
                     // Regulations Clause 9: regular classes are Mon-Fri).

// Deterministic per-offering rotation so subjects for the same batch don't all
// cluster on Monday — this IS the "round-robin across active subjects for that
// batch" rule the task allows as a first-version simplification.
startIndex = subjectOffering.id mod availableWeekdays.size()

function pickWeekdays(periodsPerWeek, occurrenceLength, offset):
    occurrencesNeeded = ceil(periodsPerWeek / occurrenceLength)
    step = floor(availableWeekdays.size() / occurrencesNeeded)
    step = max(step, 1)
    chosen = []
    for i in 0..occurrencesNeeded-1:
        idx = (startIndex + offset + i * step) mod availableWeekdays.size()
        chosen.add(availableWeekdays[idx])
    return distinct(chosen)   // dedupe if periodsPerWeek is small relative to spread

theoryWeekdays   = pickWeekdays(subject.weeklyLecturePeriods,   occurrenceLength=1,                offset=0)
labWeekdays      = pickWeekdays(subject.weeklyPracticalPeriods, occurrenceLength=subject.labDuration, offset=1)
projectWeekdays  = pickWeekdays(subject.weeklyProjectPeriods,   occurrenceLength=2,                offset=2)
tutorialWeekdays = pickWeekdays(subject.weeklyTutorialPeriods,  occurrenceLength=1,                offset=3)
```

Different `offset` values per track push THEORY/LAB/PROJECT/TUTORIAL onto
different weekdays of the same subject where possible, so a subject doesn't get
(e.g.) both its lecture and its lab nominally on the same day by coincidence of
the rotation. This weekly weekday pattern is computed **once** per
`SubjectOffering` at generation time and held fixed for the whole semester — real
timetables don't change which day a class meets from week to week, and Phase 2
should not either.

### 3.2 Traceability register — values that are computed, not printed in the source

| Value | Status |
|---|---|
| FAIML LAB unit `periodsAllotted = 5` (×6 units) | **Derived.** Source gives only "6 experiments, 30 total practical periods" (`SOURCE_MATERIAL.md` §4.1). 30 ÷ 6 = 5, applied evenly. `periodsAllottedIsDerived = true` on all 6 rows in `seed-syllabus-data.json`. |
| Every other `periodsAllotted` value (both THEORY tracks, OOP-Java PROJECT) | **Sourced directly.** "6 periods each" for all 10 theory units; "Total Periods = 60" for the Project block — both stated explicitly in `SOURCE_MATERIAL.md` §4. `periodsAllottedIsDerived = false`. |
| Nominal period-of-day slots (P1 for theory, P3–P4 for lab/project blocks) in section 4.3/5.2 | **Assumption, not from source.** SOURCE_MATERIAL.md and the existing schema give the period-timing *grid* (`CollegeTimeslot`) but no subject-to-period assignment; that is Person B's job. Chosen here only to make the worked example concrete, and non-binding on Phase 2's actual choice. |
| `ALTERNATE` Saturday/Sunday semantics (2nd & 4th) | **Assumption, not from source or from Person A's code** (grepped: `ALTERNATE` appears only as an unused enum comment on `AcademicCalendar`, nowhere implemented). Stated explicitly in section 2. |
| Every CO/Objective/Unit *text* in `seed-syllabus-data.json` | **Sourced**, transcribed from `SOURCE_MATERIAL.md` §4 with objectives/outcomes split from the source's semicolon-joined summaries into one array entry each, and parenthetical topic lists split into arrays — formatting only, no paraphrasing of technical content. |

---

## 4. Unit-sequencing / session-generation algorithm

Runs once per `(SubjectOffering, AcademicCalendar)` when `POST
/api/lesson-plan/generate` is called (section 6). Produces the full
`LessonPlanSession` list for every track the subject has.

```text
function generateLessonPlan(subjectOffering, calendar):
    teachingDates = computeTeachingDates(calendar)                    // section 2
    subject = subjectOffering.subject
    weekdayPatterns = computeWeekdayPatterns(subjectOffering, subject) // section 3

    plan = new LessonPlan(subjectOffering, calendar, generatedAt=now)
    allSessions = []
    overallSequence = 1

    for track in ["THEORY", "LAB", "PROJECT", "TUTORIAL"]:
        weeklyPeriods = periodsPerWeekFor(subject, track)   // L, P, R, or T
        if weeklyPeriods is null or weeklyPeriods == 0:
            continue   // this subject has no track of this type (e.g. OOP-Java has no LAB)

        units = SyllabusUnitRepository.findBySubjectAndSessionTypeAndActiveTrueOrderByUnitNumberAsc(subject, track)
        if units is empty:
            continue   // syllabus not yet entered for this track — nothing to generate

        occurrenceLength = occurrenceLengthFor(track, subject)  // 1 for THEORY/TUTORIAL,
                                                                  // subject.labDuration for LAB,
                                                                  // fixed 2 for PROJECT (section 3.1)
        trackDates = [d in teachingDates where dayOfWeek(d) in weekdayPatterns[track]]

        currentUnitIndex = 0
        remainingInUnit = units[0].periodsAllotted

        for date in trackDates:
            periodsLeftToday = occurrenceLength

            while periodsLeftToday > 0 and currentUnitIndex < units.size():
                unit = units[currentUnitIndex]
                take = min(periodsLeftToday, remainingInUnit)

                session = new LessonPlanSession(
                    lessonPlan = plan,
                    sessionDate = date,
                    dayOfWeek = dayOfWeek(date),
                    startTimeslot = nominalSlotFor(track),      // section 4.3
                    periodsCount = take,
                    periodType = track,
                    syllabusUnit = unit,
                    overallSequence = overallSequence++,
                    status = "PLANNED",
                    active = true
                )
                allSessions.add(session)

                periodsLeftToday -= take
                remainingInUnit -= take

                if remainingInUnit == 0:
                    currentUnitIndex += 1
                    if currentUnitIndex < units.size():
                        remainingInUnit = units[currentUnitIndex].periodsAllotted
                    // else: track's syllabus is fully sequenced; loop condition
                    // (currentUnitIndex < units.size()) stops emitting further
                    // sessions for this track even if trackDates has dates left —
                    // those remaining teaching days are free/buffer/revision time.

            if currentUnitIndex >= units.size():
                break   // nothing left to schedule for this track

    persist(plan); persist(allSessions)
    return plan, allSessions
```

Key properties this structurally guarantees (directly implementing the
problem-statement's "penalize if dependent topics are scheduled out of order" as a
hard structural rule rather than a soft solver constraint, per the task):

- `currentUnitIndex` only ever increases. A unit is never revisited and never
  scheduled before an earlier unit in the same track is fully exhausted.
- A single day/block **can** span two units back-to-back (the `while
  periodsLeftToday > 0` inner loop) — this produces two `LessonPlanSession` rows
  on the same date (correct: each row names exactly one `syllabusUnit`), which is
  the mechanism that lets sequencing "continue across day/week boundaries" as
  required.
- THEORY and LAB (or PROJECT) tracks are walked **independently** — each has its
  own `currentUnitIndex` — which is exactly the FAIML "Laboratory Integrated
  Theory" shape: two parallel tracks, each internally ordered, running across the
  same calendar weeks. OOP-Java's PROJECT track is just the degenerate case of a
  track with one unit.

### 4.1 Sanity check against the source numbers

This is a check, not a new fact: FAIML THEORY (30 periods ÷ 2/week = 15 weeks) and
FAIML LAB (30 periods ÷ 2/week = 15 weeks) finish in the same 15 weeks; OOP-Java
THEORY (30 ÷ 2/week = 15 weeks) and PROJECT (60 ÷ 4/week = 15 weeks) likewise both
finish in 15 weeks. All four tracks resolve to the same 15-teaching-week span,
comfortably inside the ~17-week, 90+-working-day semester from
`SOURCE_MATERIAL.md` §5 — the L/T/P/R inputs and the algorithm are internally
consistent with the real semester length.

### 4.2 Helper functions referenced above

```text
periodsPerWeekFor(subject, track):
    THEORY -> subject.weeklyLecturePeriods
    LAB    -> subject.weeklyPracticalPeriods
    PROJECT-> subject.weeklyProjectPeriods
    TUTORIAL -> subject.weeklyTutorialPeriods

occurrenceLengthFor(track, subject):
    THEORY, TUTORIAL -> 1
    LAB              -> subject.labDuration (existing Subject field; must be >= 2 per
                         DataReadinessService's existing validation)
    PROJECT          -> 2 (Phase-1 default; see traceability register 3.2 — not sourced)
```

### 4.3 Nominal period-of-day slot (display only, not a scheduling claim)

```text
nominalSlotFor(track):
    THEORY, TUTORIAL -> CollegeTimeslot for (dayOfWeek, periodNumber=1)   // "P1"
    LAB, PROJECT      -> CollegeTimeslot for (dayOfWeek, periodNumber=3)   // start of "P3-P4",
                                                                            // a continuous pre-lunch
                                                                            // block that never crosses
                                                                            // BREAK/LUNCH, mirroring the
                                                                            // continuity rule already
                                                                            // stated for labs in
                                                                            // SCHEDULER_CONTRACT.md §1
```

---

## 5. Example fixtures

### 5.1 Syllabus seed data

See `docs/lesson-plan/seed-syllabus-data.json` — full Units/Course
Outcomes/Objectives for both `2321CSC303J` (FAIML) and `2321CSC304R` (OOP-Java),
structured to map directly onto `SyllabusUnit`/`CourseOutcome`/`CourseObjective`
rows keyed by `subjectCode`. Phase 2 loads this by resolving `subjectCode` to the
existing `Subject.id` (already seeded by Person A / entered by an admin) and
inserting the child rows — it does not create `Subject` rows itself.

### 5.2 Worked example — OOP-Java (`2321CSC304R`), first weeks from commencement

Setup, all sourced:
- `AcademicCalendar`: `startDate = 2026-07-06` (Monday, "Class Commencement" per
  `SOURCE_MATERIAL.md` §5), `SaturdayRule = ALL_HOLIDAY`, `SundayRule =
  ALL_HOLIDAY` (Academic Regulations Clause 9: regular classes are Mon-Fri only —
  used here as the calendar setting consistent with that clause; no holidays fall
  in this window per §5, so `computeTeachingDates` yields every Mon-Fri date).
- `Subject.weeklyLecturePeriods (L) = 2`, `weeklyProjectPeriods (R) = 4`,
  `weeklyPracticalPeriods (P) = 0` (no LAB track for this subject).
- Section 3 weekday pattern used for this example: THEORY → {MONDAY, WEDNESDAY};
  PROJECT → {TUESDAY, THURSDAY} (2 periods each, occurrenceLength=2, so 2×2=4/week
  matches R=4 exactly).
- THEORY Unit 1 = "Introduction to OOP & Java Basics", `periodsAllotted = 6`.
- PROJECT Unit 1 = "Semester-long Individual/Group Project", `periodsAllotted =
  60`.

Generated `LessonPlanSession` rows, `overallSequence` 1–12 (three full weeks plus
the Monday that opens week 4, shown deliberately to demonstrate Unit 1 → Unit 2 for
THEORY crossing a week boundary exactly on schedule):

| # | sessionDate | dayOfWeek | periodType | unit | periodsCount | status | note |
|---|---|---|---|---|---|---|---|
| 1 | 2026-07-06 (Mon) | MONDAY | THEORY | Unit 1 – Intro to OOP & Java Basics | 1 | PLANNED | 1/6 periods of Unit 1 |
| 2 | 2026-07-07 (Tue) | TUESDAY | PROJECT | Unit 1 – Semester Project | 2 | PLANNED | 2/60 |
| 3 | 2026-07-08 (Wed) | WEDNESDAY | THEORY | Unit 1 | 1 | PLANNED | 2/6 |
| 4 | 2026-07-09 (Thu) | THURSDAY | PROJECT | Unit 1 | 2 | PLANNED | 4/60 |
| 5 | 2026-07-13 (Mon) | MONDAY | THEORY | Unit 1 | 1 | PLANNED | 3/6 |
| 6 | 2026-07-14 (Tue) | TUESDAY | PROJECT | Unit 1 | 2 | PLANNED | 6/60 |
| 7 | 2026-07-15 (Wed) | WEDNESDAY | THEORY | Unit 1 | 1 | PLANNED | 4/6 |
| 8 | 2026-07-16 (Thu) | THURSDAY | PROJECT | Unit 1 | 2 | PLANNED | 8/60 |
| 9 | 2026-07-20 (Mon) | MONDAY | THEORY | Unit 1 | 1 | PLANNED | 5/6 |
| 10 | 2026-07-21 (Tue) | TUESDAY | PROJECT | Unit 1 | 2 | PLANNED | 10/60 |
| 11 | 2026-07-22 (Wed) | WEDNESDAY | THEORY | **Unit 1** | 1 | PLANNED | **6/6 — Unit 1 exhausted.** Falls inside Unit Test-I (22-24 Jul, §5) — generated anyway per the section 2.1 limitation; not auto-excluded. |
| 12 | 2026-07-23 (Thu) | THURSDAY | PROJECT | Unit 1 | 2 | PLANNED | 12/60 (also inside Unit Test-I window) |
| 13 | 2026-07-27 (Mon) | MONDAY | THEORY | **Unit 2** – Methods and String Handling | 1 | PLANNED | **1/6 of Unit 2 — sequencing correctly advanced only after Unit 1's 6 periods were fully consumed, three weeks after commencement, exactly as the algorithm requires.** |

Row 13 is the load-bearing example: THEORY Unit 1 needs exactly 6 periods, is met
2×/week, so it is mathematically guaranteed to finish on the 3rd Wednesday
(6 periods ÷ 2/week = 3 weeks) — the generator produces that boundary naturally
from the period-consuming loop, with no special-cased "week 4" logic anywhere in
section 4's algorithm.

---

## 6. REST API surface

Style matches existing controllers (`@RestController`, constructor injection via
`@RequiredArgsConstructor`, `ResponseEntity<?>`, `Map.of("error", ...)` for
4xx bodies, soft-deactivation via `active=false` rather than hard delete).

### 6.1 `SyllabusController` — `/api/lesson-plan/subjects/{subjectId}`

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/units?sessionType=THEORY` (param optional) | — | `List<SyllabusUnit>` ordered by `unitNumber` |
| POST | `/units` | `SyllabusUnit` (no id) | created `SyllabusUnit`, 400 if `(subject, sessionType, unitNumber)` already active |
| PUT | `/units/{unitId}` | `SyllabusUnit` | updated `SyllabusUnit`, 404 if missing |
| DELETE | `/units/{unitId}` | — | deactivates (`active=false`), 404 if missing |
| GET | `/outcomes` | — | `List<CourseOutcome>` ordered by `coNumber` |
| POST | `/outcomes` | `CourseOutcome` | created, 400 on duplicate `coNumber` for subject |
| PUT | `/outcomes/{id}` | `CourseOutcome` | updated |
| DELETE | `/outcomes/{id}` | — | deactivates |
| GET | `/objectives` | — | `List<CourseObjective>` ordered by `sequenceNumber` |
| POST | `/objectives` | `CourseObjective` | created |
| PUT | `/objectives/{id}` | `CourseObjective` | updated |
| DELETE | `/objectives/{id}` | — | deactivates |

### 6.2 `LessonPlanController` — `/api/lesson-plan`

| Method | Path | Body / Params | Response |
|---|---|---|---|
| POST | `/generate?subjectOfferingId={id}&academicCalendarId={id}&force={bool, default false}` | — | Runs section 4's algorithm. 409 Conflict with `{"error": "An active lesson plan already exists for this offering/calendar. Pass force=true to regenerate."}` if one exists and `force` is false. On `force=true`, deactivates the prior `LessonPlan` (and its sessions) and generates a fresh one. Returns the new `LessonPlan` with `sessions: List<LessonPlanSession>` embedded. |
| GET | `/{subjectOfferingId}` | query param `academicCalendarId` optional (defaults to the offering's most recently generated active plan) | The active `LessonPlan` for that offering plus its `sessions` ordered by `overallSequence` — this is the day-by-day view. 404 if none generated yet. |
| GET | `/session/{sessionId}` | — | Single `LessonPlanSession` |
| PATCH | `/session/{sessionId}` | `{ "status": "COMPLETED" \| "RESCHEDULED", "remarks": "string, optional", "rescheduledDate": "yyyy-MM-dd, required if status=RESCHEDULED" }` | If `status=COMPLETED`: sets status + remarks on the row, done. If `status=RESCHEDULED`: creates a **new** `LessonPlanSession` on `rescheduledDate` (same `lessonPlan`, `syllabusUnit`, `periodsCount`, `periodType`; new `overallSequence` appended at the end), sets the original row's `status=RESCHEDULED` and `rescheduledTo` to point at the new row. Returns `{ "original": LessonPlanSession, "replacement": LessonPlanSession or null }`. |
| GET | `/faculty/{facultyId}` | — | All active `LessonPlan`s whose `subjectOffering.assignedFaculty.id == facultyId`, summary form (header only, no sessions) — the FACULTY-role dashboard landing list per `SOURCE_MATERIAL.md` §2 ("FACULTY role... currently just a login stub — this module is what finally gives it something to do"). |
| GET | `/my-plans` | — (uses JWT principal) | Same as above but resolves the faculty record from the authenticated `User`, for the logged-in faculty's own dashboard — reuses the existing `JwtAuthenticationFilter`/role pattern, no new auth mechanism. |

### 6.3 Error handling

Follows the existing convention exactly: no raw stack traces to the client;
`ResponseEntity.badRequest().body(Map.of("error", "..."))` for validation
failures (e.g. "Cannot generate: Subject has no active SyllabusUnit rows for
track THEORY."); `ResponseEntity.notFound().build()` for missing ids.

---

## 7. Deliverables checklist (Phase 2)

- [ ] Add the 5 nullable fields to `Subject.java` (section 1.1) — no other change to that file.
- [ ] New entities: `SyllabusUnit`, `CourseOutcome`, `CourseObjective`, `LessonPlan`, `LessonPlanSession` (sections 1.2–1.6).
- [ ] Repositories for each (Spring Data JPA, mirroring existing repository style — see `SubjectOfferingRepository.java`).
- [ ] `WorkingDayCalendarService` implementing section 2 (`computeTeachingDates`), reusable by both this module and, if useful, Person B — it depends only on `AcademicCalendar`/`CalendarHoliday`, matching Person A doc section 26's "expose calendar/working-day information through a reusable service."
- [ ] `LessonPlanGenerationService` implementing sections 3–4.
- [ ] `SyllabusController`, `LessonPlanController` implementing section 6.
- [ ] Load `seed-syllabus-data.json` (section 5.1) as an **additive** seed in `DataInitializer` — a new `seedLessonPlanSyllabusData()` method alongside the existing `seedAcademicCalendar()`, guarded by its own `if (syllabusUnitRepository.count() == 0)` check, and a **second, additive** `AcademicCalendar` row for the real Semester-III 2026-27 dates (`startDate=2026-07-06`, `endDate=2026-10-30`, per `SOURCE_MATERIAL.md` §5) alongside — not replacing — the existing placeholder Semester-5 demo calendar.
