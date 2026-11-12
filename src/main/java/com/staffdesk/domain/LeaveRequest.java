package com.staffdesk.domain;

import com.staffdesk.service.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_request")
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, length = 20)
    private LeaveType type;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "business_days", nullable = false)
    private int businessDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeaveStatus status = LeaveStatus.PENDING;

    @Column(length = 500)
    private String reason;

    @Column(name = "decided_by")
    private Long decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected LeaveRequest() {}

    public LeaveRequest(Employee employee, LeaveType type, LocalDate startDate, LocalDate endDate, int businessDays,
            String reason, LocalDateTime createdAt) {
        this.employee = employee;
        this.type = type;
        this.startDate = startDate;
        this.endDate = endDate;
        this.businessDays = businessDays;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    // Durum makinesi: yalnızca PENDING durumundaki talep karara bağlanabilir veya iptal edilebilir.

    public void approve(Long deciderId, String note, LocalDateTime now) {
        decide(LeaveStatus.APPROVED, deciderId, note, now);
    }

    public void reject(Long deciderId, String note, LocalDateTime now) {
        decide(LeaveStatus.REJECTED, deciderId, note, now);
    }

    public void cancel(LocalDateTime now) {
        requirePending();
        this.status = LeaveStatus.CANCELLED;
        this.decidedAt = now;
    }

    private void decide(LeaveStatus target, Long deciderId, String note, LocalDateTime now) {
        requirePending();
        this.status = target;
        this.decidedBy = deciderId;
        this.decidedAt = now;
        this.decisionNote = note == null || note.isBlank() ? null : note.trim();
    }

    private void requirePending() {
        if (status != LeaveStatus.PENDING) {
            throw new BusinessRuleException("Yalnızca bekleyen talepler üzerinde işlem yapılabilir (durum: " + status + ")");
        }
    }

    public Long getId() {
        return id;
    }

    public Employee getEmployee() {
        return employee;
    }

    public LeaveType getType() {
        return type;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public int getBusinessDays() {
        return businessDays;
    }

    public LeaveStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public Long getDecidedBy() {
        return decidedBy;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public String getDecisionNote() {
        return decisionNote;
    }
}
