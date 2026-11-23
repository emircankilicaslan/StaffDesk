package com.staffdesk.service;

import com.staffdesk.domain.LeaveStatus;
import com.staffdesk.domain.LeaveType;
import java.time.LocalDate;

/**
 * İzin talepleri süzgeci; boş alanlar süzgece katılmaz. from/to verilirse bu aralıkla çakışan izinler gelir.
 *
 * @param q personel adında arama
 */
public record LeaveFilter(LeaveStatus status, LeaveType type, Long employeeId, String q, LocalDate from, LocalDate to) {

    public static LeaveFilter byStatus(LeaveStatus status) {
        return new LeaveFilter(status, null, null, null, null, null);
    }
}
