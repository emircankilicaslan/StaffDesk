package com.staffdesk.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** Bir demirbaşın bir personele zimmetlenme kaydı; iade edilince returnedAt dolar (geçmiş korunur). */
@Entity
@Table(name = "asset_assignment")
public class AssetAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id")
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @Column(name = "returned_at")
    private LocalDateTime returnedAt;

    @Column(length = 300)
    private String note;

    protected AssetAssignment() {}

    public AssetAssignment(Asset asset, Employee employee, LocalDateTime assignedAt, String note) {
        this.asset = asset;
        this.employee = employee;
        this.assignedAt = assignedAt;
        this.note = note;
    }

    public void close(LocalDateTime now, String returnNote) {
        this.returnedAt = now;
        if (returnNote != null && !returnNote.isBlank()) {
            this.note = returnNote;
        }
    }

    public Long getId() {
        return id;
    }

    public Asset getAsset() {
        return asset;
    }

    public Employee getEmployee() {
        return employee;
    }

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }

    public LocalDateTime getReturnedAt() {
        return returnedAt;
    }

    public String getNote() {
        return note;
    }
}
