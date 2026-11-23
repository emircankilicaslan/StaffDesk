package com.staffdesk.service;

import com.staffdesk.api.CurrentUser;
import com.staffdesk.api.Dtos.DashboardView;
import com.staffdesk.api.Dtos.LabelCount;
import com.staffdesk.api.Dtos.MonthUsage;
import com.staffdesk.domain.Employee;
import com.staffdesk.domain.Role;
import com.staffdesk.repository.EmployeeRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rol bazlı özet panosu. Kapsam, kayıt düzeyi yetkiyle aynıdır: yönetici her şeyi, müdür yalnızca kendi birimini,
 * personel yalnızca kendi kayıtlarını görür. Toplulaştırmalar doğrudan SQL ile yapılır (H2 ve PostgreSQL uyumlu).
 */
@Service
public class DashboardService {

    /** "Garantisi yakında bitecek" sayılan gün. */
    public static final int WARRANTY_WARNING_DAYS = 60;
    static final int MONTHS = 6;

    private final JdbcTemplate jdbc;
    private final EmployeeRepository employees;
    private final Clock clock;

    public DashboardService(JdbcTemplate jdbc, EmployeeRepository employees, Clock clock) {
        this.jdbc = jdbc;
        this.employees = employees;
        this.clock = clock;
    }

    /** Sorgulara eklenen kapsam koşulu ve parametresi. */
    private record Scope(String name, String clause, List<Object> params) {}

    @Transactional(readOnly = true)
    public DashboardView summary(CurrentUser user) {
        Employee me = employees.findById(user.id()).orElseThrow(() -> new NotFoundException("Personel bulunamadı"));
        Scope scope = scopeOf(user, me);
        LocalDate today = LocalDate.now(clock);

        long active = scope.name().equals("SELF") ? 1 : count(
                "SELECT COUNT(*) FROM employee e WHERE e.active = TRUE" + scope.clause(), scope.params());
        long onLeave = count("""
                SELECT COUNT(DISTINCT l.employee_id) FROM leave_request l JOIN employee e ON e.id = l.employee_id
                WHERE l.status = 'APPROVED' AND l.start_date <= ? AND l.end_date >= ?""" + scope.clause(),
                with(scope, today, today));
        long pending = count("""
                SELECT COUNT(*) FROM leave_request l JOIN employee e ON e.id = l.employee_id
                WHERE l.status = 'PENDING'""" + scope.clause(), scope.params());

        long available = 0, assigned = 0, retired = 0, warranty = 0;
        List<LabelCount> byCategory = List.of();
        if (scope.name().equals("ALL")) {
            for (Map<String, Object> row : jdbc.queryForList("SELECT status, COUNT(*) AS n FROM asset GROUP BY status")) {
                long n = ((Number) row.get("n")).longValue();
                switch (String.valueOf(row.get("status"))) {
                    case "AVAILABLE" -> available = n;
                    case "ASSIGNED" -> assigned = n;
                    case "RETIRED" -> retired = n;
                    default -> { }
                }
            }
            warranty = count("""
                    SELECT COUNT(*) FROM asset WHERE status <> 'RETIRED' AND warranty_end IS NOT NULL
                    AND warranty_end <= ?""", List.of(today.plusDays(WARRANTY_WARNING_DAYS)));
            byCategory = jdbc.query("""
                    SELECT category, COUNT(*) AS n FROM asset WHERE status <> 'RETIRED'
                    GROUP BY category ORDER BY COUNT(*) DESC, category""",
                    (rs, i) -> new LabelCount(rs.getString("category"), rs.getLong("n")));
        } else if (scope.name().equals("SELF")) {
            assigned = count("SELECT COUNT(*) FROM asset_assignment a WHERE a.returned_at IS NULL AND a.employee_id = ?",
                    List.of(me.getId()));
        }

        return new DashboardView(scope.name(), active, onLeave, pending, available, assigned, retired, warranty,
                monthly(scope, today), leaveByType(scope, today), byCategory);
    }

    private Scope scopeOf(CurrentUser user, Employee me) {
        if (user.isAdmin()) {
            return new Scope("ALL", "", List.of());
        }
        if (user.role() == Role.MANAGER && me.getDepartment() != null) {
            return new Scope("DEPARTMENT", " AND e.department_id = ?", List.of(me.getDepartment().getId()));
        }
        return new Scope("SELF", " AND e.id = ?", List.of(me.getId()));
    }

    /** Son altı ayın (içinde bulunulan ay dahil) onaylı izin talebi ve iş günü sayısı; izin başlangıç ayına göre. */
    private List<MonthUsage> monthly(Scope scope, LocalDate today) {
        YearMonth first = YearMonth.from(today).minusMonths(MONTHS - 1L);
        List<Object> params = new ArrayList<>();
        params.add(first.atDay(1));
        params.addAll(scope.params());
        Map<YearMonth, long[]> sums = new LinkedHashMap<>();
        for (int i = 0; i < MONTHS; i++) {
            sums.put(first.plusMonths(i), new long[2]);
        }
        jdbc.query("""
                SELECT l.start_date, l.business_days FROM leave_request l JOIN employee e ON e.id = l.employee_id
                WHERE l.status = 'APPROVED' AND l.start_date >= ?""" + scope.clause(), rs -> {
            long[] bucket = sums.get(YearMonth.from(rs.getDate("start_date").toLocalDate()));
            if (bucket != null) {
                bucket[0]++;
                bucket[1] += rs.getLong("business_days");
            }
        }, params.toArray());
        List<MonthUsage> result = new ArrayList<>();
        sums.forEach((month, v) -> result.add(new MonthUsage(month.toString(), v[0], v[1])));
        return result;
    }

    private List<LabelCount> leaveByType(Scope scope, LocalDate today) {
        return jdbc.query("""
                SELECT l.leave_type AS label, COUNT(*) AS n FROM leave_request l JOIN employee e ON e.id = l.employee_id
                WHERE l.status = 'APPROVED' AND EXTRACT(YEAR FROM l.start_date) = ?""" + scope.clause()
                + " GROUP BY l.leave_type ORDER BY l.leave_type",
                (rs, i) -> new LabelCount(rs.getString("label"), rs.getLong("n")),
                with(scope, today.getYear()).toArray());
    }

    private static List<Object> with(Scope scope, Object... first) {
        List<Object> params = new ArrayList<>(List.of(first));
        params.addAll(scope.params());
        return params;
    }

    private long count(String sql, List<Object> params) {
        Long n = jdbc.queryForObject(sql, Long.class, params.toArray());
        return n == null ? 0 : n;
    }
}
