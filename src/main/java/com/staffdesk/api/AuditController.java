package com.staffdesk.api;

import com.staffdesk.api.Dtos.AuditView;
import com.staffdesk.service.AuditService;
import com.staffdesk.service.XlsxWriter;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Denetim kaydı (kim, ne zaman, ne yaptı): yalnızca ADMIN. */
@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasRole('ADMIN')")
class AuditController {

    private final AuditService audit;

    AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    Page<AuditView> list(@RequestParam(required = false) String actor, @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return audit.search(actor, action, from, to, page, size);
    }

    @GetMapping("/export.xlsx")
    ResponseEntity<byte[]> export(@RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<List<Object>> rows = audit.exportRows(actor, action, from, to).stream()
                .map(a -> List.<Object>of(a.id(), a.occurredAt(), a.actor(), a.actorRole() == null ? "" : a.actorRole(),
                        a.action(), a.entityType() == null ? "" : a.entityType(),
                        a.entityId() == null ? "" : a.entityId(), a.details() == null ? "" : a.details()))
                .toList();
        byte[] bytes = XlsxWriter.build("Denetim kaydı",
                List.of("Id", "Zaman", "Kullanıcı", "Rol", "İşlem", "Kayıt türü", "Kayıt", "Ayrıntı"), rows);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("denetim-kaydi.xlsx").build().toString())
                .contentType(MediaType.parseMediaType(XlsxWriter.CONTENT_TYPE))
                .body(bytes);
    }
}
