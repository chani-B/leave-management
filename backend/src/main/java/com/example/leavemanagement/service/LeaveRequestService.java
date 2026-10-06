package com.example.leavemanagement.service;

import com.example.leavemanagement.dto.CreateLeaveRequestDto;
import com.example.leavemanagement.dto.LeaveRequestDto;
import com.example.leavemanagement.exception.BadRequestException;
import com.example.leavemanagement.exception.InsufficientBalanceException;
import com.example.leavemanagement.exception.InvalidStateException;
import com.example.leavemanagement.exception.NotFoundException;
import com.example.leavemanagement.model.Employee;
import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;
import com.example.leavemanagement.repository.EmployeeRepository;
import com.example.leavemanagement.repository.LeaveRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Business logic for leave requests: balance rules, status transitions and transactions.
 * The controller only does HTTP mapping; repositories only do data access.
 */
@Service
public class LeaveRequestService {

    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;

    public LeaveRequestService(EmployeeRepository employeeRepository,
                               LeaveRequestRepository leaveRequestRepository) {
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestDto> findAll() {
        return leaveRequestRepository.findAllWithEmployee().stream().map(LeaveRequestDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestDto> searchByEmployeeName(String name) {
        return leaveRequestRepository.searchByEmployeeName(name).stream().map(LeaveRequestDto::from).toList();
    }

    /**
     * Creates a PENDING request. The balance check here is an early, user-friendly rejection;
     * the authoritative (locked) check happens again on approve, because balance can change
     * between submission and approval.
     */
    @Transactional
    public LeaveRequestDto create(CreateLeaveRequestDto dto) {
        Employee employee = employeeRepository.findById(dto.employeeId())
                .orElseThrow(() -> new NotFoundException("Employee " + dto.employeeId() + " not found"));

        if (dto.startDate().getYear() != dto.endDate().getYear()) {
            // Quota is annual; splitting one request across two quotas is out of scope (see DECISIONS.md).
            throw new BadRequestException("A request cannot span two calendar years; please split it.");
        }

        int days = calendarDaysInclusive(dto.startDate(), dto.endDate());

        if (dto.type() == LeaveType.VACATION) {
            ensureBalance(employee, dto.startDate().getYear(), days);
        }

        LeaveRequest request = new LeaveRequest();
        request.setEmployeeId(employee.getId());
        request.setType(dto.type());
        request.setStartDate(dto.startDate());
        request.setEndDate(dto.endDate());
        request.setDays(days);
        request.setStatus(LeaveStatus.PENDING);

        leaveRequestRepository.save(request);
        return LeaveRequestDto.from(request, employee.getName());
    }

    /**
     * Approves a PENDING request.
     *
     * Concurrency: two approvals that are each within quota could together exceed it
     * (classic check-then-act race). Inside one transaction we:
     *   1. lock the request row (FOR UPDATE) -> double-approval of the same request is impossible;
     *      the second caller blocks, then re-reads the committed status and gets 409.
     *   2. lock the employee row (FOR UPDATE) -> all vacation approvals of one employee are serialized,
     *      so the "used days" sum below always sees the other approvals' committed results.
     * Lock order is always request -> employee, so this path cannot deadlock with itself.
     */
    @Transactional
    public LeaveRequestDto approve(Long id) {
        LeaveRequest request = leaveRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Leave request " + id + " not found"));

        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new InvalidStateException(
                    "Leave request " + id + " is already " + request.getStatus().name().toLowerCase());
        }

        Employee employee = employeeRepository.findByIdForUpdate(request.getEmployeeId())
                .orElseThrow(() -> new NotFoundException("Employee " + request.getEmployeeId() + " not found"));

        if (request.getType() == LeaveType.VACATION) {
            ensureBalance(employee, request.getStartDate().getYear(), request.getDays());
        }

        request.setStatus(LeaveStatus.APPROVED); // flushed on commit (managed entity)
        return LeaveRequestDto.from(request, employee.getName());
    }

    private void ensureBalance(Employee employee, int year, int requestedDays) {
        long used = leaveRequestRepository.sumDays(
                employee.getId(), LeaveType.VACATION, LeaveStatus.APPROVED,
                LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31));

        long remaining = employee.getAnnualQuota() - used;
        if (requestedDays > remaining) {
            throw new InsufficientBalanceException(String.format(
                    "Requested %d day(s) but only %d of %d vacation day(s) remain for %d.",
                    requestedDays, Math.max(remaining, 0), employee.getAnnualQuota(), year));
        }
    }

    private static int calendarDaysInclusive(LocalDate start, LocalDate end) {
        return (int) ChronoUnit.DAYS.between(start, end) + 1;
    }
}
