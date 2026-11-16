package com.staffdesk.api;

import com.staffdesk.api.Dtos.DepartmentLeaveUsage;
import com.staffdesk.service.ReportService;
import com.staffdesk.service.XlsxWriter;
import java.time.Year;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    /** Aynı rapor Excel (.xlsx) olarak. */
    @GetMapping("/leave-usage.xlsx")
    @PreAuthorize("hasRole('ADMIN')")
    ResponseEntity<byte[]> leaveUsageXlsx(@RequestParam(required = false) Integer year) {
        int y = year == null ? Year.now().getValue() : year;
        List<List<Object>> rows = reports.leaveUsageByDepartment(y).stream()
                .map(r -> List.<Object>of(r.department(), r.employees(), r.annualDaysUsed(), r.pendingRequests()))
                .toList();
        byte[] bytes = XlsxWriter.build("İzin kullanımı " + y,
                List.of("Birim", "Personel", "Kullanılan yıllık izin (gün)", "Bekleyen talep"), rows);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("izin-kullanimi-" + y + ".xlsx").build().toString())
                .contentType(MediaType.parseMediaType(XlsxWriter.CONTENT_TYPE))
                .body(bytes);
    }
}
