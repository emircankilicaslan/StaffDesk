package com.staffdesk.service;

import com.staffdesk.api.CurrentUser;
import com.staffdesk.api.Dtos.CreateDepartmentRequest;
import com.staffdesk.api.Dtos.CreateEmployeeRequest;
import com.staffdesk.api.Dtos.DepartmentView;
import com.staffdesk.api.Dtos.EmployeeView;
import com.staffdesk.api.Dtos.UpdateEmployeeRequest;
import com.staffdesk.domain.Department;
import com.staffdesk.domain.Employee;
import com.staffdesk.domain.Role;
import com.staffdesk.repository.DepartmentRepository;
import com.staffdesk.repository.EmployeeRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
    private final AuditService audit;

    public EmployeeService(EmployeeRepository employees, DepartmentRepository departments, PasswordEncoder encoder,
            AccessPolicy access, Clock clock, AuditService audit) {
        this.audit = audit;
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
        audit.record("DEPARTMENT_CREATED", "Department", d.getId(), d.getName());
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
        audit.record("EMPLOYEE_CREATED", "Employee", e.getId(), e.getEmail() + " · " + e.getRole());
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
        return search(user, new EmployeeFilter(null, departmentId, null, null), pageable);
    }

    /** Arama ve süzgeçli liste. Müdürün birim kapsamı süzgeçten bağımsız uygulanır. */
    @Transactional(readOnly = true)
    public Page<EmployeeView> search(CurrentUser user, EmployeeFilter f, Pageable pageable) {
        Long scope = f.departmentId();
        if (!user.isAdmin()) {
            Employee manager = find(user.id());
            if (manager.getDepartment() == null) {
                return Page.empty(pageable);
            }
            scope = manager.getDepartment().getId();
        }
        Long departmentScope = scope;
        String pattern = SearchSupport.pattern(f.q());
        Specification<Employee> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (departmentScope != null) {
                p.add(cb.equal(root.join("department", JoinType.LEFT).get("id"), departmentScope));
            }
            if (f.active() != null) {
                p.add(cb.equal(root.get("active"), f.active()));
            }
            if (f.role() != null) {
                p.add(cb.equal(root.get("role"), f.role()));
            }
            if (pattern != null) {
                p.add(cb.or(
                        cb.like(cb.lower(root.<String>get("fullName")), pattern, SearchSupport.ESCAPE),
                        cb.like(cb.lower(root.<String>get("email")), pattern, SearchSupport.ESCAPE),
                        cb.like(cb.lower(root.<String>get("registrationNo")), pattern, SearchSupport.ESCAPE)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return employees.findAll(spec, pageable).map(this::toView);
    }

    /**
     * Ad, unvan, birim ve rol güncellemesi (yalnızca yönetici). Müdür rolü birim gerektirir;
     * son aktif yöneticinin rolü düşürülemez.
     */
    @Transactional
    public EmployeeView update(Long id, UpdateEmployeeRequest r) {
        Employee e = find(id);
        Department department = r.departmentId() == null ? null
                : departments.findById(r.departmentId()).orElseThrow(() -> new NotFoundException("Birim bulunamadı"));
        Role newRole = r.role() == null ? e.getRole() : r.role();
        Department effective = department != null ? department : e.getDepartment();
        if (newRole == Role.MANAGER && effective == null) {
            throw new BusinessRuleException("Müdür (MANAGER) rolü için birim zorunludur");
        }
        if (e.getRole() == Role.ADMIN && newRole != Role.ADMIN && e.isActive()
                && employees.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw new BusinessRuleException("Son aktif yöneticinin rolü değiştirilemez");
        }
        if (r.fullName() != null && r.fullName().isBlank()) {
            throw new BusinessRuleException("Ad soyad boş olamaz");
        }
        e.updateProfile(r.fullName() == null ? null : r.fullName().trim(), r.title(), department, r.role());
        audit.record("EMPLOYEE_UPDATED", "Employee", e.getId(), e.getEmail() + " · " + e.getRole());
        return toView(e);
    }

    @Transactional
    public EmployeeView activate(Long id) {
        Employee e = find(id);
        e.activate();
        audit.record("EMPLOYEE_ACTIVATED", "Employee", e.getId(), e.getEmail());
        return toView(e);
    }

    @Transactional
    public EmployeeView deactivate(Long id) {
        Employee e = find(id);
        e.deactivate();
        audit.record("EMPLOYEE_DEACTIVATED", "Employee", e.getId(), e.getEmail());
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
