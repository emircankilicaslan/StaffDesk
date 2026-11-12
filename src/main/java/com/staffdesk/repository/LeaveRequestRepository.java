package com.staffdesk.repository;

import com.staffdesk.domain.LeaveRequest;
import com.staffdesk.domain.LeaveStatus;
import java.time.LocalDate;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    /** Aynı personelin, verilen aralıkla çakışan (bekleyen/onaylı) başka izni var mı? */
    @Query("""
            select count(l) > 0 from LeaveRequest l
            where l.employee.id = :employeeId
              and l.status in :statuses
              and l.startDate <= :end and l.endDate >= :start
            """)
    boolean existsOverlap(@Param("employeeId") Long employeeId, @Param("start") LocalDate start,
            @Param("end") LocalDate end, @Param("statuses") Collection<LeaveStatus> statuses);

    /** Bir yıl içinde başlayan yıllık izinlerin, verilen durumlardaki toplam iş günü. */
    @Query("""
            select coalesce(sum(l.businessDays), 0) from LeaveRequest l
            where l.employee.id = :employeeId
              and l.type = com.staffdesk.domain.LeaveType.ANNUAL
              and l.status = :status
              and l.startDate >= :from and l.startDate <= :to
            """)
    long sumAnnualDays(@Param("employeeId") Long employeeId, @Param("status") LeaveStatus status,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    Page<LeaveRequest> findByEmployeeId(Long employeeId, Pageable pageable);

    Page<LeaveRequest> findByStatus(LeaveStatus status, Pageable pageable);

    Page<LeaveRequest> findByStatusAndEmployeeDepartmentId(LeaveStatus status, Long departmentId, Pageable pageable);
}
