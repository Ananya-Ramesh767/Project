package com.odysseus.backend.service;

import com.odysseus.backend.domain.*;
import com.odysseus.backend.repository.CollegeTimeslotRepository;
import com.odysseus.backend.repository.LessonPlanRepository;
import com.odysseus.backend.repository.LessonPlanSessionRepository;
import com.odysseus.backend.repository.SyllabusUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implements LESSON_PLAN_CONTRACT.md sections 3 (weekly period -> weekday
 * assignment) and 4 (unit-sequencing / session-generation algorithm).
 */
@Service
@RequiredArgsConstructor
public class LessonPlanGenerationService {

    private static final List<DayOfWeek> AVAILABLE_WEEKDAYS = List.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    );

    private static final List<String> TRACKS = List.of("THEORY", "LAB", "PROJECT", "TUTORIAL");

    private final WorkingDayCalendarService workingDayCalendarService;
    private final SyllabusUnitRepository syllabusUnitRepository;
    private final LessonPlanRepository lessonPlanRepository;
    private final LessonPlanSessionRepository lessonPlanSessionRepository;
    private final CollegeTimeslotRepository timeslotRepository;
    private final com.odysseus.backend.repository.TimetableAssignmentRepository assignmentRepository;

    /** Thrown for validation failures the controller should surface as 400. */
    public static class GenerationException extends RuntimeException {
        public GenerationException(String message) {
            super(message);
        }
    }

    public LessonPlan generate(SubjectOffering subjectOffering, AcademicCalendar calendar) {
        Subject subject = subjectOffering.getSubject();
        List<LocalDate> teachingDates = workingDayCalendarService.computeTeachingDates(calendar);
        Map<String, List<DayOfWeek>> weekdayPatterns = computeWeekdayPatterns(subjectOffering, subject);

        LessonPlan plan = LessonPlan.builder()
                .subjectOffering(subjectOffering)
                .academicCalendar(calendar)
                .generatedAt(LocalDateTime.now())
                .active(true)
                .build();

        List<LessonPlanSession> allSessions = new ArrayList<>();
        List<String> notesParts = new ArrayList<>();
        List<String> tracksMissingSyllabus = new ArrayList<>();
        boolean anyTrackApplicable = false;
        boolean anyTrackGenerated = false;
        int overallSequence = 1;

        for (String track : TRACKS) {
            Integer weeklyPeriods = periodsPerWeekFor(subject, track);
            if (weeklyPeriods == null || weeklyPeriods == 0) {
                continue; // this subject has no track of this type
            }
            if ("LAB".equals(track) && !subject.isRequiresLab()) {
                continue;
            }
            anyTrackApplicable = true;

            List<SyllabusUnit> units = syllabusUnitRepository
                    .findBySubjectAndSessionTypeAndActiveTrueOrderByUnitNumberAsc(subject, track);
            if (units.isEmpty()) {
                tracksMissingSyllabus.add(track); // syllabus not yet entered for this track
                continue;
            }

            int occurrenceLength = occurrenceLengthFor(track, subject);
            List<DayOfWeek> trackWeekdays = weekdayPatterns.getOrDefault(track, List.of());
            List<LocalDate> trackDates = teachingDates.stream()
                    .filter(d -> trackWeekdays.contains(d.getDayOfWeek()))
                    .collect(Collectors.toList());

            int currentUnitIndex = 0;
            int remainingInUnit = units.get(0).getPeriodsAllotted();

            for (LocalDate date : trackDates) {
                if (currentUnitIndex >= units.size()) {
                    break; // nothing left to schedule for this track
                }
                int periodsLeftToday = occurrenceLength;

                while (periodsLeftToday > 0 && currentUnitIndex < units.size()) {
                    SyllabusUnit unit = units.get(currentUnitIndex);
                    int take = Math.min(periodsLeftToday, remainingInUnit);

                    LessonPlanSession session = LessonPlanSession.builder()
                            .lessonPlan(plan)
                            .sessionDate(date)
                            .dayOfWeek(date.getDayOfWeek().name())
                            .startTimeslot(nominalSlotFor(track, date.getDayOfWeek().name()))
                            .periodsCount(take)
                            .periodType(track)
                            .syllabusUnit(unit)
                            .overallSequence(overallSequence++)
                            .status("PLANNED")
                            .active(true)
                            .build();
                    allSessions.add(session);

                    periodsLeftToday -= take;
                    remainingInUnit -= take;

                    if (remainingInUnit == 0) {
                        currentUnitIndex++;
                        if (currentUnitIndex < units.size()) {
                            remainingInUnit = units.get(currentUnitIndex).getPeriodsAllotted();
                        }
                        // else: track's syllabus is fully sequenced.
                    }
                }
            }

            anyTrackGenerated = true;
            notesParts.add(track + ": " + trackWeekdays.stream().map(Enum::name).collect(Collectors.joining("/"))
                    + " (" + occurrenceLength + "p)");
        }

        if (!anyTrackApplicable) {
            throw new GenerationException("Cannot generate: Subject has no configured weekly periods (L/T/P/R) for any track.");
        }
        if (!anyTrackGenerated) {
            String tracks = String.join(", ", tracksMissingSyllabus);
            throw new GenerationException("Cannot generate: Subject has no active SyllabusUnit rows for track " + tracks + ".");
        }
        if (!tracksMissingSyllabus.isEmpty()) {
            notesParts.add("No syllabus entered yet for: " + String.join(", ", tracksMissingSyllabus));
        }

        plan.setGenerationNotes(String.join("; ", notesParts));

        LessonPlan savedPlan = lessonPlanRepository.save(plan);
        for (LessonPlanSession s : allSessions) {
            s.setLessonPlan(savedPlan);
        }
        lessonPlanSessionRepository.saveAll(allSessions);

        return savedPlan;
    }

    Map<String, List<DayOfWeek>> computeWeekdayPatterns(SubjectOffering subjectOffering, Subject subject) {
        Map<String, List<DayOfWeek>> patterns = new HashMap<>();

        // Check if an approved timetable exists for this offering's batch and subject
        if (subjectOffering.getBatch() != null && subjectOffering.getSubject() != null) {
            List<com.odysseus.backend.domain.TimetableAssignment> timetableList =
                    assignmentRepository.findByBatchIdAndActiveTrue(subjectOffering.getBatch().getId());
            List<DayOfWeek> timetableDays = timetableList.stream()
                    .filter(a -> a.getSubject() != null && java.util.Objects.equals(a.getSubject().getId(), subject.getId()))
                    .map(a -> {
                        try {
                            return DayOfWeek.valueOf(a.getDayOfWeek().toUpperCase());
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            if (!timetableDays.isEmpty()) {
                patterns.put("THEORY", timetableDays);
                patterns.put("LAB", timetableDays);
                patterns.put("PROJECT", timetableDays);
                patterns.put("TUTORIAL", timetableDays);
                return patterns;
            }
        }

        int startIndex = Math.floorMod(subjectOffering.getId() != null ? subjectOffering.getId() : 1L, AVAILABLE_WEEKDAYS.size());

        patterns.put("THEORY", pickWeekdays(startIndex, periodsPerWeekFor(subject, "THEORY"), 1, 0));
        if (subject.isRequiresLab()) {
            int labOccurrenceLength = subject.getLabDuration() != null ? subject.getLabDuration() : 2;
            patterns.put("LAB", pickWeekdays(startIndex, periodsPerWeekFor(subject, "LAB"), labOccurrenceLength, 1));
        }
        patterns.put("PROJECT", pickWeekdays(startIndex, periodsPerWeekFor(subject, "PROJECT"), 2, 2));
        patterns.put("TUTORIAL", pickWeekdays(startIndex, periodsPerWeekFor(subject, "TUTORIAL"), 1, 3));

        return patterns;
    }

    private List<DayOfWeek> pickWeekdays(int startIndex, Integer periodsPerWeek, int occurrenceLength, int offset) {
        if (periodsPerWeek == null || periodsPerWeek <= 0) {
            return List.of();
        }
        int occurrencesNeeded = (int) Math.ceil(periodsPerWeek / (double) occurrenceLength);
        occurrencesNeeded = Math.max(occurrencesNeeded, 1);
        int size = AVAILABLE_WEEKDAYS.size();
        int step = Math.max(size / occurrencesNeeded, 1);

        LinkedHashSet<DayOfWeek> chosen = new LinkedHashSet<>();
        for (int i = 0; i < occurrencesNeeded; i++) {
            int idx = Math.floorMod(startIndex + offset + i * step, size);
            chosen.add(AVAILABLE_WEEKDAYS.get(idx));
        }
        return new ArrayList<>(chosen);
    }

    private Integer periodsPerWeekFor(Subject subject, String track) {
        switch (track) {
            case "THEORY":
                if (subject.getWeeklyLecturePeriods() != null && subject.getWeeklyLecturePeriods() > 0) {
                    return subject.getWeeklyLecturePeriods();
                }
                if (subject.getTheoryCredits() > 0) {
                    return (int) Math.round(subject.getTheoryCredits());
                }
                if (!subject.isRequiresLab() && subject.getCalculatedWeeklyHours() > 0) {
                    return (int) Math.round(subject.getCalculatedWeeklyHours());
                }
                return null;
            case "LAB":
                if (subject.getWeeklyPracticalPeriods() != null && subject.getWeeklyPracticalPeriods() > 0) {
                    return subject.getWeeklyPracticalPeriods();
                }
                if (subject.getLabCredits() > 0) {
                    return (int) Math.round(subject.getLabCredits() * 2);
                }
                if (subject.isRequiresLab()) {
                    return subject.getLabDuration() != null ? subject.getLabDuration() : 2;
                }
                return null;
            case "PROJECT":
                return subject.getWeeklyProjectPeriods();
            case "TUTORIAL":
                if (subject.getWeeklyTutorialPeriods() != null && subject.getWeeklyTutorialPeriods() > 0) {
                    return subject.getWeeklyTutorialPeriods();
                }
                if (subject.getTutorialCredits() > 0) {
                    return (int) Math.round(subject.getTutorialCredits());
                }
                return null;
            default:
                return null;
        }
    }

    private int occurrenceLengthFor(String track, Subject subject) {
        switch (track) {
            case "THEORY":
            case "TUTORIAL":
                return 1;
            case "LAB":
                return subject.getLabDuration() != null ? subject.getLabDuration() : 2;
            case "PROJECT":
                return 2; // Phase-1 default; see contract traceability register 3.2
            default:
                return 1;
        }
    }

    private CollegeTimeslot nominalSlotFor(String track, String dayOfWeek) {
        int periodNumber = ("THEORY".equals(track) || "TUTORIAL".equals(track)) ? 1 : 3;
        return timeslotRepository.findByDayOfWeekAndActiveTrue(dayOfWeek).stream()
                .filter(t -> !t.isBreak() && !t.isLunch())
                .filter(t -> t.getPeriodNumber() != null && t.getPeriodNumber() == periodNumber)
                .findFirst()
                .orElse(null);
    }
}
