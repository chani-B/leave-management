package com.example.leavemanagement.dto;

import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;

import java.time.LocalDate;

/**
 * API representation of a leave request. Decouples the HTTP contract from the JPA entity
 * (no lazy-loading / circular-reference surprises, no accidental exposure of new columns).
 * type/status keep their numeric JSON shape (see the enums) so the existing client contract holds.
 */
public record LeaveRequestDto(
        Long id,
        Long employeeId,
        String employeeName,
        LeaveType type,
        LocalDate startDate,
        LocalDate endDate,
        LeaveStatus status,
        int days
) {
    public static LeaveRequestDto from(LeaveRequest r, String employeeName) {
        return new LeaveRequestDto(r.getId(), r.getEmployeeId(), employeeName, r.getType(),
                r.getStartDate(), r.getEndDate(), r.getStatus(), r.getDays());
    }

    public static LeaveRequestDto from(LeaveRequest r) {
        return from(r, r.getEmployee() != null ? r.getEmployee().getName() : null);
    }
}
