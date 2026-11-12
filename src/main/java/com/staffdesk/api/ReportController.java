package com.staffdesk.api;

import com.staffdesk.api.Dtos.DepartmentLeaveUsage;
import com.staffdesk.service.ReportService;
import java.time.Year;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
class ReportController {

    private final ReportService reports;

    ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/leave-usage")
    @PreAuthorize("hasRole('ADMIN')")
    List<DepartmentLeaveUsage> leaveUsage(@RequestParam(required = false) Integer year) {
        return reports.leaveUsageByDepartment(year == null ? Year.now().getValue() : year);
    }
}
