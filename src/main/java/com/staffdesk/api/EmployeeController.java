package com.staffdesk.api;

import com.staffdesk.api.Dtos.CreateDepartmentRequest;
import com.staffdesk.api.Dtos.CreateEmployeeRequest;
import com.staffdesk.api.Dtos.DepartmentView;
import com.staffdesk.api.Dtos.EmployeeView;
import com.staffdesk.api.Dtos.LeaveBalance;
import com.staffdesk.service.EmployeeService;
import com.staffdesk.service.LeaveService;
import jakarta.validation.Valid;
import java.time.Year;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class EmployeeController {

    static final int MAX_PAGE_SIZE = 100;

    private final EmployeeService employees;
    private final LeaveService leaves;

    EmployeeController(EmployeeService employees, LeaveService leaves) {
        this.employees = employees;
        this.leaves = leaves;
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    DepartmentView createDepartment(@Valid @RequestBody CreateDepartmentRequest request) {
        return employees.createDepartment(request);
    }

    @GetMapping("/departments")
    List<DepartmentView> departments() {
        return employees.listDepartments();
    }

    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    EmployeeView create(@Valid @RequestBody CreateEmployeeRequest request) {
        return employees.create(request);
    }

    @GetMapping("/employees")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    Page<EmployeeView> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) Long departmentId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return employees.list(CurrentUser.from(jwt), departmentId, pageable(page, size));
    }

    @GetMapping("/employees/me")
    EmployeeView me(@AuthenticationPrincipal Jwt jwt) {
        CurrentUser user = CurrentUser.from(jwt);
        return employees.get(user.id(), user);
    }

    @GetMapping("/employees/{id}")
    EmployeeView get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return employees.get(id, CurrentUser.from(jwt));
    }

    @PatchMapping("/employees/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    EmployeeView deactivate(@PathVariable Long id) {
        return employees.deactivate(id);
    }

    @GetMapping("/employees/{id}/leave-balance")
    LeaveBalance balance(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
            @RequestParam(required = false) Integer year) {
        return leaves.balance(id, year == null ? Year.now().getValue() : year, CurrentUser.from(jwt));
    }

    static PageRequest pageable(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page >= 0 ve 1 <= size <= " + MAX_PAGE_SIZE + " olmalı");
        }
        return PageRequest.of(page, size, Sort.by("id"));
    }
}
