package com.example.leavemanagement.dto;

import com.example.leavemanagement.model.LeaveType;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

/**
 * Incoming payload for creating a leave request.
 * Input-shape validation lives here (Bean Validation); business rules live in the service.
 */
public record CreateLeaveRequestDto(
        @NotNull(message = "employeeId is required")
        @Positive(message = "employeeId must be positive")
        Long employeeId,

        @NotNull(message = "type is required")
        LeaveType type,

        @NotNull(message = "startDate is required")
        LocalDate startDate,

        @NotNull(message = "endDate is required")
        LocalDate endDate
) {

    /** Cross-field rule: start must not be after end (otherwise the day count would be <= 0). */
    @JsonIgnore
    @AssertTrue(message = "startDate must be on or before endDate")
    public boolean isDateRangeValid() {
        // Null fields are reported by @NotNull; don't double-report them here.
        return startDate == null || endDate == null || !startDate.isAfter(endDate);
    }
}
