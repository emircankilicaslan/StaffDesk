package com.staffdesk.service;

import com.staffdesk.api.CurrentUser;
import com.staffdesk.api.Dtos.LeaveBalance;
import com.staffdesk.api.Dtos.LeaveCreateRequest;
import com.staffdesk.api.Dtos.LeaveView;
import com.staffdesk.domain.Employee;
import com.staffdesk.domain.LeaveRequest;
import com.staffdesk.domain.LeaveStatus;
import com.staffdesk.domain.LeaveType;
import com.staffdesk.domain.Role;
import com.staffdesk.repository.EmployeeRepository;
import com.staffdesk.repository.LeaveRequestRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeaveService {

    static final int MAX_RANGE_DAYS = 60;
    static final int MAX_SICK_BACKDATE_DAYS = 30;
    private static final Set<LeaveStatus> ACTIVE = Set.of(LeaveStatus.PENDING, LeaveStatus.APPROVED);

    private final LeaveRequestRepository leaves;
    private final EmployeeRepository employees;
    private final BusinessDayCalculator businessDays;
    private final AccessPolicy access;
    private final Clock clock;

    public LeaveService(LeaveRequestRepository leaves, EmployeeRepository employees, BusinessDayCalculator businessDays,
            AccessPolicy access, Clock clock) {
        this.leaves = leaves;
        this.employees = employees;
        this.businessDays = businessDays;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public LeaveView request(Long employeeId, LeaveCreateRequest r) {
        Employee employee = employees.findById(employeeId).orElseThrow(() -> new NotFoundException("Personel bulunamadı"));
        if (!employee.isActive()) {
            throw new BusinessRuleException("Pasif personel izin talep edemez");
        }
        validateDates(r);

        int days = businessDays.count(r.startDate(), r.endDate());
        if (days == 0) {
            throw new BusinessRuleException("Seçilen aralıkta iş günü yok (hafta sonu/resmi tatil)");
        }
        if (leaves.existsOverlap(employeeId, r.startDate(), r.endDate(), ACTIVE)) {
            throw new BusinessRuleException("Bu tarih aralığında başka bir izin talebiniz var");
        }
        if (r.type() == LeaveType.ANNUAL) {
            LeaveBalance balance = balance(employee, r.startDate().getYear(), r.startDate());
            if (days > balance.remaining()) {
                throw new BusinessRuleException("Yetersiz yıllık izin bakiyesi: kalan %d iş günü, talep edilen %d"
                        .formatted(balance.remaining(), days));
            }
        }

        LeaveRequest saved = leaves.save(new LeaveRequest(employee, r.type(), r.startDate(), r.endDate(), days,
                r.reason(), LocalDateTime.now(clock)));
        return toView(saved);
    }

    private void validateDates(LeaveCreateRequest r) {
        LocalDate today = LocalDate.now(clock);
        if (r.endDate().isBefore(r.startDate())) {
            throw new BusinessRuleException("Bitiş tarihi başlangıç tarihinden önce olamaz");
        }
        if (ChronoUnit.DAYS.between(r.startDate(), r.endDate()) + 1 > MAX_RANGE_DAYS) {
            throw new BusinessRuleException("Tek talepte en fazla " + MAX_RANGE_DAYS + " günlük aralık seçilebilir");
        }
        if (r.type() == LeaveType.SICK) {
            if (r.startDate().isBefore(today.minusDays(MAX_SICK_BACKDATE_DAYS))) {
                throw new BusinessRuleException("Hastalık izni en fazla " + MAX_SICK_BACKDATE_DAYS
                        + " gün geriye dönük bildirilebilir");
            }
        } else if (r.startDate().isBefore(today)) {
            throw new BusinessRuleException("Yıllık ve mazeret izni geçmiş tarih için talep edilemez");
        }
    }

    /** Yıl içindeki hak ediş (hizmet süresine göre), onaylı ve bekleyen yıllık izin günleri ve kalan bakiye. */
    @Transactional(readOnly = true)
    public LeaveBalance balance(Long employeeId, int year, CurrentUser user) {
        Employee employee = employees.findById(employeeId).orElseThrow(() -> new NotFoundException("Personel bulunamadı"));
        access.requireCanView(user, employee);
        return balance(employee, year, LocalDate.of(year, 12, 31));
    }

    private LeaveBalance balance(Employee employee, int year, LocalDate asOf) {
        int entitlement = LeaveEntitlementCalculator.annualDays(employee.getHireDate(), asOf);
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 31);
        long used = leaves.sumAnnualDays(employee.getId(), LeaveStatus.APPROVED, from, to);
        long pending = leaves.sumAnnualDays(employee.getId(), LeaveStatus.PENDING, from, to);
        return new LeaveBalance(year, entitlement, used, pending, Math.max(0, entitlement - used - pending));
    }

    @Transactional(readOnly = true)
    public Page<LeaveView> mine(Long employeeId, Pageable pageable) {
        return leaves.findByEmployeeId(employeeId, pageable).map(this::toView);
    }

    /** Yönetici tüm talepleri, müdür yalnızca kendi birimindekileri görür. */
    @Transactional(readOnly = true)
    public Page<LeaveView> listByStatus(CurrentUser user, LeaveStatus status, Pageable pageable) {
        if (user.isAdmin()) {
            return leaves.findByStatus(status, pageable).map(this::toView);
        }
        Employee manager = employees.findById(user.id()).orElseThrow(() -> new NotFoundException("Personel bulunamadı"));
        if (manager.getDepartment() == null) {
            return Page.empty(pageable);
        }
        return leaves.findByStatusAndEmployeeDepartmentId(status, manager.getDepartment().getId(), pageable)
                .map(this::toView);
    }

    @Transactional
    public LeaveView approve(Long id, CurrentUser decider, String note) {
        LeaveRequest l = decidable(id, decider);
        l.approve(decider.id(), note, LocalDateTime.now(clock));
        return toView(l);
    }

    @Transactional
    public LeaveView reject(Long id, CurrentUser decider, String note) {
        LeaveRequest l = decidable(id, decider);
        l.reject(decider.id(), note, LocalDateTime.now(clock));
        return toView(l);
    }

    @Transactional
    public LeaveView cancel(Long id, Long employeeId) {
        LeaveRequest l = find(id);
        if (!l.getEmployee().getId().equals(employeeId)) {
            throw new AccessDeniedException("Yalnızca kendi talebinizi iptal edebilirsiniz");
        }
        l.cancel(LocalDateTime.now(clock));
        return toView(l);
    }

    /** Karar yetkisi: kimse kendi talebini onaylayamaz; müdür yalnızca kendi birimini onaylar. */
    private LeaveRequest decidable(Long id, CurrentUser decider) {
        LeaveRequest l = find(id);
        if (l.getEmployee().getId().equals(decider.id())) {
            throw new AccessDeniedException("Kendi talebiniz hakkında karar veremezsiniz");
        }
        if (decider.role() == Role.MANAGER) {
            access.requireCanView(decider, l.getEmployee());
        }
        return l;
    }

    private LeaveRequest find(Long id) {
        return leaves.findById(id).orElseThrow(() -> new NotFoundException("İzin talebi bulunamadı"));
    }

    private LeaveView toView(LeaveRequest l) {
        return new LeaveView(l.getId(), l.getEmployee().getId(), l.getEmployee().getFullName(), l.getType(),
                l.getStartDate(), l.getEndDate(), l.getBusinessDays(), l.getStatus(), l.getReason(), l.getDecidedBy(),
                l.getDecidedAt(), l.getDecisionNote());
    }
}
