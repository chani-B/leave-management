package com.example.leavemanagement;

import com.example.leavemanagement.model.Employee;
import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;
import com.example.leavemanagement.repository.EmployeeRepository;
import com.example.leavemanagement.repository.LeaveRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Runs against a real, throwaway PostgreSQL started by Testcontainers.
// (Docker must be available on the machine running the tests.)
// Tests go through HTTP (MockMvc) so they also cover validation, status codes and JSON shape.
// Each test creates its own employee, so tests don't depend on each other or on seed data.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class LeaveRequestsTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired EmployeeRepository employees;
    @Autowired LeaveRequestRepository leaveRequests;

    // ---------- create ----------

    @Test
    void create_WithinQuota_Succeeds() throws Exception {
        Employee emp = employee("Test Emp", 20);
        long before = leaveRequests.count();

        mvc.perform(post("/api/leave-requests").contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(emp.getId(), 0, "2026-03-01", "2026-03-03")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.days").value(3))
                .andExpect(jsonPath("$.status").value(0))
                .andExpect(jsonPath("$.employeeName").value("Test Emp"));

        assertEquals(before + 1, leaveRequests.count());
    }

    /** Regression test for the balance bug: already-approved days must count against the quota. */
    @Test
    void create_ExceedingRemainingBalance_IsRejected() throws Exception {
        Employee emp = employee("Almost Out", 20);
        approved(emp, LocalDate.of(2026, 1, 6), 18); // 2 days left
        long before = leaveRequests.count();

        mvc.perform(post("/api/leave-requests").contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(emp.getId(), 0, "2026-03-01", "2026-03-03"))) // 3 days
                .andExpect(status().isUnprocessableEntity());

        assertEquals(before, leaveRequests.count());
    }

    @Test
    void create_ExactlyRemainingBalance_Succeeds() throws Exception {
        Employee emp = employee("Boundary", 20);
        approved(emp, LocalDate.of(2026, 1, 6), 18);

        mvc.perform(post("/api/leave-requests").contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(emp.getId(), 0, "2026-03-01", "2026-03-02"))) // exactly 2
                .andExpect(status().isCreated());
    }

    @Test
    void create_PreviousYearUsage_DoesNotCount() throws Exception {
        Employee emp = employee("New Year", 20);
        approved(emp, LocalDate.of(2025, 6, 1), 18); // last year's leave

        mvc.perform(post("/api/leave-requests").contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(emp.getId(), 0, "2026-03-01", "2026-03-05")))
                .andExpect(status().isCreated());
    }

    @Test
    void create_SickLeave_IsNotLimitedByVacationQuota() throws Exception {
        Employee emp = employee("Sick", 5);
        approved(emp, LocalDate.of(2026, 1, 6), 5);

        mvc.perform(post("/api/leave-requests").contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(emp.getId(), 1, "2026-03-01", "2026-03-03")))
                .andExpect(status().isCreated());
    }

    @Test
    void create_StartAfterEnd_Returns400() throws Exception {
        Employee emp = employee("Backwards", 20);

        mvc.perform(post("/api/leave-requests").contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(emp.getId(), 0, "2026-03-05", "2026-03-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dateRangeValid").exists());
    }

    @Test
    void create_MissingType_Returns400() throws Exception {
        Employee emp = employee("No Type", 20);

        mvc.perform(post("/api/leave-requests").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeId\":" + emp.getId()
                                + ",\"startDate\":\"2026-03-01\",\"endDate\":\"2026-03-02\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.type").exists());
    }

    @Test
    void create_UnknownEmployee_Returns404() throws Exception {
        mvc.perform(post("/api/leave-requests").contentType(MediaType.APPLICATION_JSON)
                        .content(createJson(999_999L, 0, "2026-03-01", "2026-03-02")))
                .andExpect(status().isNotFound());
    }

    // ---------- search (security) ----------

    @Test
    void search_SqlInjectionPayload_IsTreatedAsPlainText() throws Exception {
        Employee emp = employee("Injection Target", 20);
        approved(emp, LocalDate.of(2026, 2, 1), 1);

        // With the old string-concatenated SQL this returned every row in the table.
        mvc.perform(get("/api/leave-requests/search").param("name", "zzz') OR 1=1 --"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---------- helpers ----------

    private Employee employee(String name, int quota) {
        Employee e = new Employee();
        e.setName(name);
        e.setAnnualQuota(quota);
        return employees.save(e);
    }

    private LeaveRequest approved(Employee emp, LocalDate start, int days) {
        return request(emp, start, days, LeaveStatus.APPROVED);
    }

    private LeaveRequest request(Employee emp, LocalDate start, int days, LeaveStatus status) {
        LeaveRequest r = new LeaveRequest();
        r.setEmployeeId(emp.getId());
        r.setType(LeaveType.VACATION);
        r.setStartDate(start);
        r.setEndDate(start.plusDays(days - 1L));
        r.setDays(days);
        r.setStatus(status);
        return leaveRequests.save(r);
    }

    private static String createJson(Long employeeId, int type, String start, String end) {
        return "{\"employeeId\":" + employeeId + ",\"type\":" + type
                + ",\"startDate\":\"" + start + "\",\"endDate\":\"" + end + "\"}";
    }
}
