package com.easwari.backend.service;

import com.easwari.backend.domain.*;
import com.easwari.backend.dto.SchedulerInputContractDto;
import com.easwari.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SchedulerDataService {

    private final SubjectRepository subjectRepository;
    private final FacultyRepository facultyRepository;
    private final RoomRepository roomRepository;
    private final BatchRepository batchRepository;
    private final SubjectOfferingRepository offeringRepository;
    private final CollegeTimeslotRepository timeslotRepository;
    private final NepCreditRuleRepository nepRuleRepository;

    public SchedulerInputContractDto buildSchedulerInputPayload() {
        NepCreditRule activeRule = nepRuleRepository.findFirstByActiveTrueOrderByIdDesc()
                .orElse(NepCreditRule.builder()
                        .theoryHoursPerCredit(1.0)
                        .labHoursPerCredit(2.0)
                        .tutorialHoursPerCredit(1.0)
                        .build());

        // Metadata
        SchedulerInputContractDto.Metadata metadata = SchedulerInputContractDto.Metadata.builder()
                .academicYear("2026-2027")
                .semester(5)
                .institution("Odysseus Engineering College")
                .contractVersion("1.0.0")
                .generatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .build();

        // Subjects
        List<SchedulerInputContractDto.SubjectDto> subjects = subjectRepository.findByActiveTrue().stream()
                .map(s -> SchedulerInputContractDto.SubjectDto.builder()
                        .subjectId(s.getId())
                        .subjectCode(s.getSubjectCode())
                        .subjectName(s.getSubjectName())
                        .department(s.getDepartment())
                        .semester(s.getSemester())
                        .subjectType(s.getSubjectType())
                        .theoryCredits(s.getTheoryCredits())
                        .labCredits(s.getLabCredits())
                        .tutorialCredits(s.getTutorialCredits())
                        .theoryHoursPerWeek(s.getTheoryCredits() * activeRule.getTheoryHoursPerCredit())
                        .labHoursPerWeek(s.getLabCredits() * activeRule.getLabHoursPerCredit())
                        .tutorialHoursPerWeek(s.getTutorialCredits() * activeRule.getTutorialHoursPerCredit())
                        .totalWeeklyHours(s.getCalculatedWeeklyHours())
                        .requiresLab(s.isRequiresLab())
                        .labDurationPeriods(s.getLabDuration())
                        .build())
                .collect(Collectors.toList());

        // Faculty
        List<SchedulerInputContractDto.FacultyDto> faculty = facultyRepository.findByActiveTrue().stream()
                .map(f -> SchedulerInputContractDto.FacultyDto.builder()
                        .facultyId(f.getId())
                        .employeeId(f.getEmployeeId())
                        .name(f.getName())
                        .email(f.getEmail())
                        .department(f.getDepartment())
                        .maxWeeklyLoad(f.getMaxWeeklyLoad())
                        .eligibleSubjectIds(f.getEligibleSubjects().stream().map(Subject::getId).collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());

        // Rooms
        List<SchedulerInputContractDto.RoomDto> rooms = roomRepository.findByActiveTrue().stream()
                .map(r -> SchedulerInputContractDto.RoomDto.builder()
                        .roomId(r.getId())
                        .roomCode(r.getRoomCode())
                        .roomName(r.getRoomName())
                        .roomType(r.getRoomType())
                        .capacity(r.getCapacity())
                        .department(r.getDepartment())
                        .build())
                .collect(Collectors.toList());

        // Batches
        List<SchedulerInputContractDto.BatchDto> batches = batchRepository.findByActiveTrue().stream()
                .map(b -> SchedulerInputContractDto.BatchDto.builder()
                        .batchId(b.getId())
                        .batchName(b.getBatchName())
                        .section(b.getSection())
                        .department(b.getDepartment())
                        .semester(b.getSemester())
                        .strength(b.getStrength())
                        .build())
                .collect(Collectors.toList());

        // Offerings
        List<SchedulerInputContractDto.OfferingDto> offerings = offeringRepository.findByActiveTrue().stream()
                .map(o -> {
                    List<Long> eligibleFacultyIds = facultyRepository.findByActiveTrue().stream()
                            .filter(f -> f.getEligibleSubjects().contains(o.getSubject()))
                            .map(Faculty::getId)
                            .collect(Collectors.toList());

                    return SchedulerInputContractDto.OfferingDto.builder()
                            .offeringId(o.getId())
                            .batchId(o.getBatch().getId())
                            .subjectId(o.getSubject().getId())
                            .assignedFacultyId(o.getAssignedFaculty() != null ? o.getAssignedFaculty().getId() : null)
                            .eligibleFacultyIds(eligibleFacultyIds)
                            .build();
                })
                .collect(Collectors.toList());

        // Timeslots
        List<SchedulerInputContractDto.TimeslotDto> timeslots = timeslotRepository.findByActiveTrue().stream()
                .map(t -> SchedulerInputContractDto.TimeslotDto.builder()
                        .timeslotId(t.getId())
                        .dayOfWeek(t.getDayOfWeek())
                        .periodNumber(t.getPeriodNumber())
                        .periodLabel(t.getPeriodLabel())
                        .startTime(t.getStartTime().toString())
                        .endTime(t.getEndTime().toString())
                        .isBreak(t.isBreak())
                        .isLunch(t.isLunch())
                        .build())
                .collect(Collectors.toList());

        return SchedulerInputContractDto.builder()
                .metadata(metadata)
                .subjects(subjects)
                .faculty(faculty)
                .rooms(rooms)
                .batches(batches)
                .offerings(offerings)
                .timeslots(timeslots)
                .build();
    }
}
