package com.staffdesk.service;

import com.staffdesk.api.Dtos.AuditView;
import com.staffdesk.domain.AuditLog;
import com.staffdesk.repository.AuditLogRepository;
import com.staffdesk.repository.EmployeeRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Denetim kaydı yazar ve sorgular.
 * <ul>
 *   <li>{@link #record}: işlemi yapan kullanıcı oturumdan okunur; kayıt, çağıranın işlemine katılır, yani asıl
 *       değişiklikle aynı işlemde (atomik) yazılır.</li>
 *   <li>{@link #recordIndependent}: giriş olayları için; başarısız girişte işlem geri alınsa da kayıt kalsın diye
 *       ayrı bir işlemde yazılır.</li>
 * </ul>
 */
@Service
public class AuditService {

    static final int MAX_EXPORT_ROWS = 10_000;

    private final AuditLogRepository logs;
    private final EmployeeRepository employees;
    private final Clock clock;

    public AuditService(AuditLogRepository logs, EmployeeRepository employees, Clock clock) {
        this.logs = logs;
        this.employees = employees;
        this.clock = clock;
    }

    @Transactional
    public void record(String action, String entityType, Object entityId, String details) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Long actorId = null;
        String label = "sistem";
        String role = null;
        if (auth instanceof JwtAuthenticationToken) {
            try {
                actorId = Long.valueOf(auth.getName());
                label = employees.findById(actorId).map(e -> e.getEmail()).orElse("#" + actorId);
                role = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                        .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a).findFirst().orElse(null);
            } catch (NumberFormatException ignored) {
                // konu sayı değilse sistem olarak kaydedilir
            }
        }
        logs.save(new AuditLog(LocalDateTime.now(clock), actorId, label, role, action, entityType,
                entityId == null ? null : String.valueOf(entityId), details));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordIndependent(Long actorId, String actorLabel, String role, String action, String entityType,
            Object entityId, String details) {
        logs.save(new AuditLog(LocalDateTime.now(clock), actorId, actorLabel, role, action, entityType,
                entityId == null ? null : String.valueOf(entityId), details));
    }

    @Transactional(readOnly = true)
    public Page<AuditView> search(String actor, String action, LocalDate from, LocalDate to, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Sayfa 0 veya daha büyük olmalı");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Sayfa boyutu 1-100 arasında olmalı");
        }
        return logs.findAll(spec(actor, action, from, to), PageRequest.of(page, size, order())).map(AuditService::toView);
    }

    @Transactional(readOnly = true)
    public List<AuditView> exportRows(String actor, String action, LocalDate from, LocalDate to) {
        return logs.findAll(spec(actor, action, from, to), PageRequest.of(0, MAX_EXPORT_ROWS, order()))
                .map(AuditService::toView).getContent();
    }

    private static Sort order() {
        return Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by(Sort.Direction.DESC, "id"));
    }

    private static Specification<AuditLog> spec(String actor, String action, LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Başlangıç tarihi bitişten sonra olamaz");
        }
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (actor != null && !actor.isBlank()) {
                p.add(cb.equal(cb.lower(root.get("actorLabel")), actor.trim().toLowerCase()));
            }
            if (action != null && !action.isBlank()) {
                p.add(cb.equal(root.get("action"), action.trim()));
            }
            if (from != null) {
                p.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("occurredAt"), from.atStartOfDay()));
            }
            if (to != null) {
                p.add(cb.lessThan(root.<LocalDateTime>get("occurredAt"), to.plusDays(1).atStartOfDay()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    private static AuditView toView(AuditLog a) {
        return new AuditView(a.getId(), a.getOccurredAt(), a.getActorLabel(), a.getActorRole(), a.getAction(),
                a.getEntityType(), a.getEntityId(), a.getDetails());
    }
}
