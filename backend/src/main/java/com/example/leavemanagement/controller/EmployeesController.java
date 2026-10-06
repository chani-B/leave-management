package com.example.leavemanagement.controller;

import com.example.leavemanagement.dto.EmployeeDto;
import com.example.leavemanagement.repository.EmployeeRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Read-only lookup so the UI can offer an employee picker.
// Deliberately calls the repository directly: there is no business logic here, so a
// pass-through service would be ceremony (see DECISIONS.md). Returns DTOs, not entities.
@RestController
@RequestMapping("/api/employees")
public class EmployeesController {

    private final EmployeeRepository employeeRepository;

    public EmployeesController(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @GetMapping
    public List<EmployeeDto> getAll() {
        return employeeRepository.findAll().stream().map(EmployeeDto::from).toList();
    }
}
