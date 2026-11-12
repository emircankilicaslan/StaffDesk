package com.staffdesk.service;

import com.staffdesk.api.CurrentUser;
import com.staffdesk.api.Dtos.CreateDepartmentRequest;
import com.staffdesk.api.Dtos.CreateEmployeeRequest;
import com.staffdesk.api.Dtos.DepartmentView;
import com.staffdesk.api.Dtos.EmployeeView;
import com.staffdesk.domain.Department;
import com.staffdesk.domain.Employee;
import com.staffdesk.domain.Role;
import com.staffdesk.repository.DepartmentRepository;
import com.staffdesk.repository.EmployeeRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeService {

    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final PasswordEncoder encoder;
    private final AccessPolicy access;
    private final Clock clock;

    public EmployeeService(EmployeeRepository employees, DepartmentRepository departments, PasswordEncoder encoder,
            AccessPolicy access, Clock clock) {
        this.employees = employees;
        this.departments = departments;
        this.encoder = encoder;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public DepartmentView createDepartment(CreateDepartmentRequest request) {
        String name = request.name().trim();
        if (departments.existsByNameIgnoreCase(name)) {
            throw new BusinessRuleException("Bu isimde bir birim zaten var");
        }
        Department d = departments.save(new Department(name));
        return new DepartmentView(d.getId(), d.getName());
    }

    @Transactional(readOnly = true)
    public List<DepartmentView> listDepartments() {
        return departments.findAll().stream().map(d -> new DepartmentView(d.getId(), d.getName())).toList();
    }

    @Transactional
    public EmployeeView create(CreateEmployeeRequest r) {
        String email = r.email().trim().toLowerCase();
        if (employees.existsByEmailIgnoreCase(email)) {
            throw new BusinessRuleException("Bu e-posta zaten kayıtlı");
        }
        if (employees.existsByRegistrationNo(r.registrationNo().trim())) {
            throw new BusinessRuleException("Bu sicil numarası zaten kayıtlı");
        }
        Department department = r.departmentId() == null ? null
                : departments.findById(r.departmentId()).orElseThrow(() -> new NotFoundException("Birim bulunamadı"));
        if (r.role() == Role.MANAGER && department == null) {
            throw new BusinessRuleException("Müdür (MANAGER) rolü için birim zorunludur");
        }
        Employee e = employees.save(new Employee(r.registrationNo().trim(), r.fullName().trim(), email,
                encoder.encode(r.password()), r.role(), r.title(), department, r.hireDate(),
                LocalDateTime.now(clock)));
        return toView(e);
    }

    @Transactional(readOnly = true)
    public EmployeeView get(Long id, CurrentUser user) {
        Employee e = find(id);
        access.requireCanView(user, e);
        return toView(e);
    }

    /** Yönetici tüm personeli, müdür yalnızca kendi birimini listeler. */
    @Transactional(readOnly = true)
    public Page<EmployeeView> list(CurrentUser user, Long departmentId, Pageable pageable) {
        Long scope = departmentId;
        if (!user.isAdmin()) {
            Employee manager = find(user.id());
            if (manager.getDepartment() == null) {
                return Page.empty(pageable);
            }
            scope = manager.getDepartment().getId();
        }
        Page<Employee> page = scope == null ? employees.findAll(pageable) : employees.findByDepartmentId(scope, pageable);
        return page.map(this::toView);
    }

    @Transactional
    public EmployeeView deactivate(Long id) {
        Employee e = find(id);
        e.deactivate();
        return toView(e);
    }

    Employee find(Long id) {
        return employees.findById(id).orElseThrow(() -> new NotFoundException("Personel bulunamadı"));
    }

    EmployeeView toView(Employee e) {
        Department d = e.getDepartment();
        return new EmployeeView(e.getId(), e.getRegistrationNo(), e.getFullName(), e.getEmail(), e.getRole(),
                e.getTitle(), d == null ? null : d.getId(), d == null ? null : d.getName(), e.getHireDate(),
                e.isActive());
    }
}
