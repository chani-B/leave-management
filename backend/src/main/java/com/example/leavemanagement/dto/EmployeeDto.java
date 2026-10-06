package com.example.leavemanagement.dto;

import com.example.leavemanagement.model.Employee;

public record EmployeeDto(Long id, String name, int annualQuota) {
    public static EmployeeDto from(Employee e) {
        return new EmployeeDto(e.getId(), e.getName(), e.getAnnualQuota());
    }
}
