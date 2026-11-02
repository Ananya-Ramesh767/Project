package com.easwari.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimetableValidationResult {

    private boolean valid;
    private String message;
    private String conflictType;
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    public static TimetableValidationResult success(String message) {
        return TimetableValidationResult.builder()
                .valid(true)
                .message(message)
                .errors(new ArrayList<>())
                .build();
    }

    public static TimetableValidationResult failure(String conflictType, String error) {
        List<String> errors = new ArrayList<>();
        errors.add(error);
        return TimetableValidationResult.builder()
                .valid(false)
                .conflictType(conflictType)
                .message(error)
                .errors(errors)
                .build();
    }

    public static TimetableValidationResult failure(String conflictType, String message, List<String> errors) {
        return TimetableValidationResult.builder()
                .valid(false)
                .conflictType(conflictType)
                .message(message)
                .errors(errors != null ? errors : new ArrayList<>())
                .build();
    }
}
