package com.staffdesk.service;

import com.staffdesk.api.CurrentUser;
import com.staffdesk.api.Dtos.CalendarEntry;
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
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
    private final AuditService audit;

    public LeaveService(LeaveRequestRepository leaves, EmployeeRepository employees, BusinessDayCalculator businessDays,
            AccessPolicy access, Clock clock, AuditService audit) {
        this.audit = audit;
        this.leaves = leaves;
        this.employees = employees;
        this.businessDays = businessDays;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public LeaveView request(Long employeeId, LeaveCreateRequest r) {
        // Aynı personelin eş zamanlı talepleri sıraya girer: çakışma ve bakiye kontrolü yarışa açık kalmasın.
        Employee employee = employees.findByIdForUpdate(employeeId).orElseThrow(() -> new NotFoundException("Personel bulunamadı"));
        if (!employee.isActive()) {
            throw new BusinessRuleException("Pasif personel izin talep edemez");
        }
        validateDates(r);
        businessDays.requireCalendar(r.startDate(), r.endDate());

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
        audit.record("LEAVE_REQUESTED", "LeaveRequest", saved.getId(),
                r.type() + " · " + r.startDate() + " – " + r.endDate() + " · " + days + " iş günü");
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
        return search(user, LeaveFilter.byStatus(status), pageable);
    }

    /** Süzgeçli liste (durum, tür, personel, ad arama, tarih aralığı). Müdürün birim kapsamı süzgeçten bağımsızdır. */
    @Transactional(readOnly = true)
    public Page<LeaveView> search(CurrentUser user, LeaveFilter f, Pageable pageable) {
        if (f.from() != null && f.to() != null && f.from().isAfter(f.to())) {
            throw new IllegalArgumentException("Başlangıç tarihi bitişten sonra olamaz");
        }
        Long scope = null;
        if (!user.isAdmin()) {
            Employee manager = employees.findById(user.id()).orElseThrow(() -> new NotFoundException("Personel bulunamadı"));
            if (manager.getDepartment() == null) {
                return Page.empty(pageable);
            }
            scope = manager.getDepartment().getId();
        }
        Long departmentScope = scope;
        String pattern = SearchSupport.pattern(f.q());
        Specification<LeaveRequest> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            Join<LeaveRequest, Employee> e = root.join("employee");
            if (departmentScope != null) {
                p.add(cb.equal(e.join("department", JoinType.LEFT).get("id"), departmentScope));
            }
            if (f.status() != null) {
                p.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.type() != null) {
                p.add(cb.equal(root.get("type"), f.type()));
            }
            if (f.employeeId() != null) {
                p.add(cb.equal(e.get("id"), f.employeeId()));
            }
            if (pattern != null) {
                p.add(cb.like(cb.lower(e.<String>get("fullName")), pattern, SearchSupport.ESCAPE));
            }
            if (f.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("endDate"), f.from()));
            }
            if (f.to() != null) {
                p.add(cb.lessThanOrEqualTo(root.<LocalDate>get("startDate"), f.to()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return leaves.findAll(spec, pageable).map(this::toView);
    }

    /**
     * Ekip takvimi: verilen ayla çakışan izinler. Yönetici tüm birimleri (bekleyen dahil), müdür kendi birimini
     * (bekleyen dahil) görür; personel yalnızca kendi birimindeki <b>onaylı</b> izinleri görür (gerekçe hiçbir
     * rolde dönmez). Birimi olmayan personel yalnızca kendi izinlerini görür.
     */
    @Transactional(readOnly = true)
    public List<CalendarEntry> calendar(CurrentUser user, YearMonth requested) {
        YearMonth month = requested == null ? YearMonth.now(clock) : requested;
        Employee me = employees.findById(user.id()).orElseThrow(() -> new NotFoundException("Personel bulunamadı"));
        Long departmentScope = user.isAdmin() || me.getDepartment() == null ? null : me.getDepartment().getId();
        boolean onlyMine = !user.isAdmin() && me.getDepartment() == null;
        Collection<LeaveStatus> statuses = user.role() == Role.EMPLOYEE ? Set.of(LeaveStatus.APPROVED) : ACTIVE;
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        Specification<LeaveRequest> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            Join<LeaveRequest, Employee> e = root.join("employee");
            if (departmentScope != null) {
                p.add(cb.equal(e.join("department", JoinType.LEFT).get("id"), departmentScope));
            }
            if (onlyMine) {
                p.add(cb.equal(e.get("id"), user.id()));
            }
            p.add(root.get("status").in(statuses));
            p.add(cb.lessThanOrEqualTo(root.<LocalDate>get("startDate"), to));
            p.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("endDate"), from));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return leaves.findAll(spec, Sort.by("startDate").and(Sort.by("id"))).stream()
                .map(l -> new CalendarEntry(l.getEmployee().getId(), l.getEmployee().getFullName(),
                        l.getEmployee().getDepartment() == null ? null : l.getEmployee().getDepartment().getName(),
                        l.getType(), l.getStartDate(), l.getEndDate(), l.getStatus()))
                .toList();
    }

    @Transactional
    public LeaveView approve(Long id, CurrentUser decider, String note) {
        LeaveRequest l = decidable(id, decider);
        l.approve(decider.id(), note, LocalDateTime.now(clock));
        audit.record("LEAVE_APPROVED", "LeaveRequest", l.getId(), note);
        return toView(l);
    }

    @Transactional
    public LeaveView reject(Long id, CurrentUser decider, String note) {
        LeaveRequest l = decidable(id, decider);
        l.reject(decider.id(), note, LocalDateTime.now(clock));
        audit.record("LEAVE_REJECTED", "LeaveRequest", l.getId(), note);
        return toView(l);
    }

    @Transactional
    public LeaveView cancel(Long id, Long employeeId) {
        LeaveRequest l = find(id);
        if (!l.getEmployee().getId().equals(employeeId)) {
            throw new AccessDeniedException("Yalnızca kendi talebinizi iptal edebilirsiniz");
        }
        l.cancel(LocalDateTime.now(clock));
        audit.record("LEAVE_CANCELLED", "LeaveRequest", l.getId(), null);
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
