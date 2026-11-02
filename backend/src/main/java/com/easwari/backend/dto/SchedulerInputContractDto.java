package com.easwari.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchedulerInputContractDto {

    private Metadata metadata;
    private List<SubjectDto> subjects;
    private List<FacultyDto> faculty;
    private List<RoomDto> rooms;
    private List<BatchDto> batches;
    private List<OfferingDto> offerings;
    private List<TimeslotDto> timeslots;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metadata {
        private String academicYear;
        private Integer semester;
        private String institution;
        private String contractVersion;
        private String generatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubjectDto {
        private Long subjectId;
        private String subjectCode;
        private String subjectName;
        private String department;
        private Integer semester;
        private String subjectType;
        private double theoryCredits;
        private double labCredits;
        private double tutorialCredits;
        private double theoryHoursPerWeek;
        private double labHoursPerWeek;
        private double tutorialHoursPerWeek;
        private double totalWeeklyHours;
        private boolean requiresLab;
        private Integer labDurationPeriods;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FacultyDto {
        private Long facultyId;
        private String employeeId;
        private String name;
        private String email;
        private String department;
        private Integer maxWeeklyLoad;
        private List<Long> eligibleSubjectIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoomDto {
        private Long roomId;
        private String roomCode;
        private String roomName;
        private String roomType; // LECTURE_HALL, LAB
        private Integer capacity;
        private String department;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchDto {
        private Long batchId;
        private String batchName;
        private String section;
        private String department;
        private Integer semester;
        private Integer strength;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OfferingDto {
        private Long offeringId;
        private Long batchId;
        private Long subjectId;
        private Long assignedFacultyId;
        private List<Long> eligibleFacultyIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeslotDto {
        private Long timeslotId;
        private String dayOfWeek;
        private Integer periodNumber;
        private String periodLabel;
        private String startTime;
        private String endTime;
        private boolean isBreak;
        private boolean isLunch;
    }
}
