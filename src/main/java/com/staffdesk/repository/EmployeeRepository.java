package com.staffdesk.repository;

import com.staffdesk.domain.Employee;
import com.staffdesk.domain.Role;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRegistrationNo(String registrationNo);

    boolean existsByRole(Role role);

    Page<Employee> findByDepartmentId(Long departmentId, Pageable pageable);

    /** Personeli yazma kilidiyle okur: aynı personelin eş zamanlı izin talepleri sıraya girer. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where e.id = :id")
    Optional<Employee> findByIdForUpdate(@Param("id") Long id);
}
