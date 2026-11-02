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
public class DataReadinessResult {
    private boolean ready;
    private int issueCount;
    private List<String> issues;
    private List<String> blockingIssues;
    private List<String> warnings;
    private String summaryMessage;
    // Scope context for traceability
    private String department;
    private String semester;
    private String academicYear;
    // Counts for quick summary
    private int subjectsChecked;
    private int offeringsChecked;
    private int batchesChecked;
}
