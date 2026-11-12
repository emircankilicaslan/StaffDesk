package com.staffdesk.repository;

import com.staffdesk.domain.Employee;
import com.staffdesk.domain.Role;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRegistrationNo(String registrationNo);

    boolean existsByRole(Role role);

    Page<Employee> findByDepartmentId(Long departmentId, Pageable pageable);
}
