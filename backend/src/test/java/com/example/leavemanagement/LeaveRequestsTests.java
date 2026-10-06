package com.example.leavemanagement;

import com.example.leavemanagement.model.Employee;
import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;
import com.example.leavemanagement.repository.EmployeeRepository;
import com.example.leavemanagement.repository.LeaveRequestRepository;
import com.example.leavemanagement.exception.InsufficientBalanceException;
import com.example.leavemanagement.service.LeaveRequestService;
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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
    @Autowired LeaveRequestService service;

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

    // ---------- approve ----------

    @Test
    void approve_Pending_Returns200AndApproves() throws Exception {
        Employee emp = employee("Approve Me", 20);
        LeaveRequest r = request(emp, LocalDate.of(2026, 4, 1), 3, LeaveStatus.PENDING);

        mvc.perform(post("/api/leave-requests/{id}/approve", r.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1));

        assertEquals(LeaveStatus.APPROVED, leaveRequests.findById(r.getId()).orElseThrow().getStatus());
    }

    @Test
    void approve_AlreadyApproved_Returns409() throws Exception {
        Employee emp = employee("Twice", 20);
        LeaveRequest r = approved(emp, LocalDate.of(2026, 4, 1), 3);

        mvc.perform(post("/api/leave-requests/{id}/approve", r.getId()))
                .andExpect(status().isConflict());
    }

    @Test
    void approve_Rejected_Returns409() throws Exception {
        Employee emp = employee("Rejected", 20);
        LeaveRequest r = request(emp, LocalDate.of(2026, 4, 1), 3, LeaveStatus.REJECTED);

        mvc.perform(post("/api/leave-requests/{id}/approve", r.getId()))
                .andExpect(status().isConflict());
    }

    @Test
    void approve_NotFound_Returns404() throws Exception {
        mvc.perform(post("/api/leave-requests/{id}/approve", 999_999L))
                .andExpect(status().isNotFound());
    }

    /** Balance may have changed since the request was submitted -> re-checked on approve. */
    @Test
    void approve_WhenBalanceNoLongerSuffices_Returns422() throws Exception {
        Employee emp = employee("Changed Since", 10);
        LeaveRequest first = request(emp, LocalDate.of(2026, 5, 1), 6, LeaveStatus.PENDING);
        LeaveRequest second = request(emp, LocalDate.of(2026, 6, 1), 6, LeaveStatus.PENDING);

        mvc.perform(post("/api/leave-requests/{id}/approve", first.getId())).andExpect(status().isOk());
        mvc.perform(post("/api/leave-requests/{id}/approve", second.getId()))
                .andExpect(status().isUnprocessableEntity());

        assertEquals(LeaveStatus.PENDING, leaveRequests.findById(second.getId()).orElseThrow().getStatus());
    }

    /**
     * Two approvals racing for the same employee: each fits the quota alone, together they don't.
     * Exactly one must win. Without the row locks in LeaveRequestService#approve both threads can
     * read "used = 0" and both commit (lost update / write skew).
     * Note: a passing run doesn't prove absence of a race, but a regression will fail this
     * test most of the time.
     */
    @Test
    void approve_ConcurrentApprovals_NeverExceedQuota() throws Exception {
        Employee emp = employee("Race", 10);
        LeaveRequest a = request(emp, LocalDate.of(2026, 7, 1), 6, LeaveStatus.PENDING);
        LeaveRequest b = request(emp, LocalDate.of(2026, 8, 1), 6, LeaveStatus.PENDING);

        List<Throwable> outcomes = runConcurrently(
                () -> service.approve(a.getId()),
                () -> service.approve(b.getId()));

        long successes = outcomes.stream().filter(t -> t == null).count();
        assertEquals(1, successes, "exactly one approval should succeed, got: " + outcomes);
        outcomes.stream().filter(t -> t != null)
                .forEach(t -> assertInstanceOf(InsufficientBalanceException.class, t));

        long approvedDays = leaveRequests.sumDays(emp.getId(), LeaveType.VACATION, LeaveStatus.APPROVED,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertEquals(6, approvedDays);
    }

    /** Same request approved twice at the same time -> one 200, one InvalidState (409). */
    @Test
    void approve_SameRequestConcurrently_ApprovedOnce() throws Exception {
        Employee emp = employee("Double Click", 20);
        LeaveRequest r = request(emp, LocalDate.of(2026, 9, 1), 2, LeaveStatus.PENDING);

        List<Throwable> outcomes = runConcurrently(
                () -> service.approve(r.getId()),
                () -> service.approve(r.getId()));

        assertEquals(1, outcomes.stream().filter(t -> t == null).count(), "outcomes: " + outcomes);
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

    /** Starts all tasks at the same instant; returns null for success or the thrown exception, per task. */
    @SafeVarargs
    private static List<Throwable> runConcurrently(Callable<?>... tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.length);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Throwable>> futures = java.util.Arrays.stream(tasks)
                    .map(task -> pool.submit(() -> {
                        go.await();
                        try {
                            task.call();
                            return (Throwable) null;
                        } catch (Throwable t) {
                            return t;
                        }
                    }))
                    .toList();
            go.countDown();
            List<Throwable> results = new java.util.ArrayList<>();
            for (Future<Throwable> f : futures) {
                results.add(f.get());
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static String createJson(Long employeeId, int type, String start, String end) {
        return "{\"employeeId\":" + employeeId + ",\"type\":" + type
                + ",\"startDate\":\"" + start + "\",\"endDate\":\"" + end + "\"}";
    }
}
