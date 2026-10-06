package com.example.leavemanagement.repository;

import com.example.leavemanagement.model.LeaveRequest;
import com.example.leavemanagement.model.LeaveStatus;
import com.example.leavemanagement.model.LeaveType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    /** All requests with their employee in one query (avoids N+1), newest first. Sorting is done by the DB. */
    @Query("select r from LeaveRequest r join fetch r.employee order by r.startDate desc, r.id desc")
    List<LeaveRequest> findAllWithEmployee();

    /** Search by employee name. The value is a bound parameter -> no SQL injection. */
    @Query("""
            select r from LeaveRequest r join fetch r.employee e
            where lower(e.name) like lower(concat('%', :name, '%'))
            order by r.startDate desc, r.id desc
            """)
    List<LeaveRequest> searchByEmployeeName(@Param("name") String name);

    /** Sum of days for an employee/type/status whose start date falls in [from, to]. Done in SQL, not in Java. */
    @Query("""
            select coalesce(sum(r.days), 0) from LeaveRequest r
            where r.employeeId = :employeeId and r.type = :type and r.status = :status
              and r.startDate between :from and :to
            """)
    long sumDays(@Param("employeeId") Long employeeId,
                 @Param("type") LeaveType type,
                 @Param("status") LeaveStatus status,
                 @Param("from") LocalDate from,
                 @Param("to") LocalDate to);
}
