package com.easwari.backend.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class SessionUpdateRequestDto {
    private String status; // COMPLETED | RESCHEDULED
    private String remarks;
    private LocalDate rescheduledDate; // required if status = RESCHEDULED
}
