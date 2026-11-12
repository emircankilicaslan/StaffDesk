package com.staffdesk.service;

import com.staffdesk.api.Dtos.DepartmentLeaveUsage;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    /**
     * Birim bazında yıllık izin kullanımı. Toplulaştırma, JPA yerine doğrudan SQL ile yapılır:
     * LEFT JOIN + GROUP BY ile izni olmayan birimler de listelenir.
     */
    private static final String LEAVE_USAGE_SQL = """
            SELECT d.name AS department,
                   COUNT(DISTINCT e.id) AS employees,
                   COALESCE(SUM(CASE WHEN l.status = 'APPROVED' AND l.leave_type = 'ANNUAL'
                                     THEN l.business_days ELSE 0 END), 0) AS annual_days_used,
                   COALESCE(SUM(CASE WHEN l.status = 'PENDING' THEN 1 ELSE 0 END), 0) AS pending_requests
            FROM department d
            LEFT JOIN employee e ON e.department_id = d.id AND e.active = TRUE
            LEFT JOIN leave_request l ON l.employee_id = e.id AND EXTRACT(YEAR FROM l.start_date) = ?
            GROUP BY d.name
            ORDER BY d.name
            """;

    private final JdbcTemplate jdbc;

    public ReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<DepartmentLeaveUsage> leaveUsageByDepartment(int year) {
        return jdbc.query(LEAVE_USAGE_SQL,
                (rs, i) -> new DepartmentLeaveUsage(rs.getString("department"), rs.getLong("employees"),
                        rs.getLong("annual_days_used"), rs.getLong("pending_requests")),
                year);
    }
}
