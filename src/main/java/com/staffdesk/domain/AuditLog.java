package com.staffdesk.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Denetim kaydı: kim (actorLabel: e-posta), ne zaman, hangi işlemi, hangi kayıt üzerinde yaptı.
 * Parola ve token hiçbir zaman kayda girmez. Kayıtlar yalnızca eklenir.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_label", nullable = false, length = 254)
    private String actorLabel;

    @Column(name = "actor_role", length = 20)
    private String actorRole;

    @Column(nullable = false, length = 40)
    private String action;

    @Column(name = "entity_type", length = 30)
    private String entityType;

    @Column(name = "entity_id", length = 40)
    private String entityId;

    @Column(length = 500)
    private String details;

    protected AuditLog() {}

    public AuditLog(LocalDateTime occurredAt, Long actorId, String actorLabel, String actorRole, String action,
            String entityType, String entityId, String details) {
        this.occurredAt = occurredAt;
        this.actorId = actorId;
        this.actorLabel = cut(actorLabel, 254);
        this.actorRole = cut(actorRole, 20);
        this.action = cut(action, 40);
        this.entityType = cut(entityType, 30);
        this.entityId = cut(entityId, 40);
        this.details = cut(details, 500);
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public Long getId() { return id; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public Long getActorId() { return actorId; }
    public String getActorLabel() { return actorLabel; }
    public String getActorRole() { return actorRole; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public String getDetails() { return details; }
}
