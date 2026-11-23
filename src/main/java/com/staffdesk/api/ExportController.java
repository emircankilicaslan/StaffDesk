package com.staffdesk.api;

import com.staffdesk.api.Dtos.AssetView;
import com.staffdesk.api.Dtos.EmployeeView;
import com.staffdesk.api.Dtos.LeaveView;
import com.staffdesk.domain.AssetStatus;
import com.staffdesk.domain.LeaveStatus;
import com.staffdesk.domain.LeaveType;
import com.staffdesk.domain.Role;
import com.staffdesk.service.AssetFilter;
import com.staffdesk.service.AssetService;
import com.staffdesk.service.AuditService;
import com.staffdesk.service.EmployeeFilter;
import com.staffdesk.service.EmployeeService;
import com.staffdesk.service.LeaveFilter;
import com.staffdesk.service.LeaveService;
import com.staffdesk.service.XlsxWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Excel (.xlsx) dışa aktarma; yalnızca yönetici. Her indirme denetim kaydına yazılır. */
@RestController
@RequestMapping("/api/exports")
@PreAuthorize("hasRole('ADMIN')")
class ExportController {

    static final int MAX_ROWS = 10_000;

    private static final Map<Role, String> ROLE = Map.of(Role.ADMIN, "Yönetici", Role.MANAGER, "Müdür", Role.EMPLOYEE, "Personel");
    private static final Map<LeaveType, String> TYPE = Map.of(
            LeaveType.ANNUAL, "Yıllık izin", LeaveType.SICK, "Hastalık izni", LeaveType.EXCUSE, "Mazeret izni");
    private static final Map<LeaveStatus, String> LEAVE_STATUS = Map.of(
            LeaveStatus.PENDING, "Bekliyor", LeaveStatus.APPROVED, "Onaylandı", LeaveStatus.REJECTED, "Reddedildi",
            LeaveStatus.CANCELLED, "İptal edildi");
    private static final Map<AssetStatus, String> ASSET_STATUS = Map.of(
            AssetStatus.AVAILABLE, "Boşta", AssetStatus.ASSIGNED, "Zimmetli", AssetStatus.RETIRED, "Kayıttan düşüldü");

    private final EmployeeService employees;
    private final AssetService assets;
    private final LeaveService leaves;
    private final AuditService audit;

    ExportController(EmployeeService employees, AssetService assets, LeaveService leaves, AuditService audit) {
        this.employees = employees;
        this.assets = assets;
        this.leaves = leaves;
        this.audit = audit;
    }

    @GetMapping("/employees.xlsx")
    ResponseEntity<byte[]> employees(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String q,
            @RequestParam(required = false) Long departmentId, @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Role role) {
        List<EmployeeView> list = employees.search(CurrentUser.from(jwt), new EmployeeFilter(q, departmentId, active, role),
                PageRequest.of(0, MAX_ROWS, Sort.by("fullName").and(Sort.by("id")))).getContent();
        List<List<Object>> rows = list.stream().map(e -> List.<Object>of(e.registrationNo(), e.fullName(), e.email(),
                ROLE.get(e.role()), e.title() == null ? "" : e.title(), e.department() == null ? "" : e.department(),
                e.hireDate(), e.active())).toList();
        return file("personel", "Personel", List.of("Sicil no", "Ad soyad", "E-posta", "Rol", "Unvan", "Birim",
                "İşe giriş", "Aktif"), rows);
    }

    @GetMapping("/assets.xlsx")
    ResponseEntity<byte[]> assets(@RequestParam(required = false) String q, @RequestParam(required = false) String category,
            @RequestParam(required = false) AssetStatus status, @RequestParam(required = false) Integer warrantyWithinDays) {
        List<AssetView> list = assets.search(new AssetFilter(q, category, status, warrantyWithinDays),
                PageRequest.of(0, MAX_ROWS, Sort.by("assetTag").and(Sort.by("id")))).getContent();
        List<List<Object>> rows = list.stream().map(a -> {
            List<Object> r = new java.util.ArrayList<>();
            r.add(a.assetTag());
            r.add(a.name());
            r.add(a.category());
            r.add(ASSET_STATUS.get(a.status()));
            r.add(a.serialNo());
            r.add(a.purchaseDate());
            r.add(a.purchasePrice());
            r.add(a.warrantyEnd());
            r.add(a.assignedTo());
            return r;
        }).toList();
        return file("demirbaslar", "Demirbaşlar", List.of("Demirbaş no", "Ad", "Kategori", "Durum", "Seri no",
                "Satın alma", "Fiyat (TL)", "Garanti bitiş", "Zimmetli"), rows);
    }

    @GetMapping("/leaves.xlsx")
    ResponseEntity<byte[]> leaves(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "ALL") String status,
            @RequestParam(required = false) LeaveType type, @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LeaveStatus parsed = "ALL".equalsIgnoreCase(status) ? null : LeaveStatus.valueOf(status.toUpperCase(java.util.Locale.ROOT));
        List<LeaveView> list = leaves.search(CurrentUser.from(jwt), new LeaveFilter(parsed, type, employeeId, q, from, to),
                PageRequest.of(0, MAX_ROWS, Sort.by(Sort.Direction.DESC, "startDate").and(Sort.by("id")))).getContent();
        List<List<Object>> rows = list.stream().map(l -> {
            List<Object> r = new java.util.ArrayList<>();
            r.add(l.employeeName());
            r.add(TYPE.get(l.type()));
            r.add(l.startDate());
            r.add(l.endDate());
            r.add(l.businessDays());
            r.add(LEAVE_STATUS.get(l.status()));
            r.add(l.decisionNote());
            return r;
        }).toList();
        return file("izinler", "İzinler", List.of("Personel", "Tür", "Başlangıç", "Bitiş", "İş günü", "Durum",
                "Karar notu"), rows);
    }

    private ResponseEntity<byte[]> file(String name, String sheet, List<String> headers, List<List<Object>> rows) {
        audit.record("EXPORT", "Export", name, rows.size() + " satır");
        byte[] bytes = XlsxWriter.build(sheet, headers, rows);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name + "-" + LocalDate.now() + ".xlsx").build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(XlsxWriter.CONTENT_TYPE))
                .body(bytes);
    }
}
