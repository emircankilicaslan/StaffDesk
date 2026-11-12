package com.staffdesk.service;

import com.staffdesk.api.CurrentUser;
import com.staffdesk.domain.Employee;
import com.staffdesk.domain.Role;
import com.staffdesk.repository.EmployeeRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * Kayıt düzeyi yetki kuralları: kullanıcı kendi kaydını görebilir; yönetici her kaydı;
 * müdür (MANAGER) yalnızca kendi biriminin personelini görebilir.
 */
@Component
public class AccessPolicy {

    private final EmployeeRepository employees;

    public AccessPolicy(EmployeeRepository employees) {
        this.employees = employees;
    }

    public void requireCanView(CurrentUser user, Employee target) {
        if (!canView(user, target)) {
            throw new AccessDeniedException("Bu kayda erişim yetkiniz yok");
        }
    }

    public boolean canView(CurrentUser user, Employee target) {
        if (user.isAdmin() || user.id().equals(target.getId())) {
            return true;
        }
        if (user.role() != Role.MANAGER || target.getDepartment() == null) {
            return false;
        }
        Employee manager = employees.findById(user.id()).orElse(null);
        return manager != null && manager.getDepartment() != null
                && manager.getDepartment().getId().equals(target.getDepartment().getId());
    }
}
