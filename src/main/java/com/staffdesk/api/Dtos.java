package com.staffdesk.api;

import com.staffdesk.domain.AssetStatus;
import com.staffdesk.domain.LeaveStatus;
import com.staffdesk.domain.LeaveType;
import com.staffdesk.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** API istek ve yanıt modelleri (entity'ler dışarıya doğrudan açılmaz). */
public final class Dtos {

    private Dtos() {}

    // --- Kimlik doğrulama
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record TokenResponse(String token, long expiresInSeconds, Role role) {}

    // --- Birim ve personel
    public record CreateDepartmentRequest(@NotBlank @Size(max = 100) String name) {}

    public record DepartmentView(Long id, String name) {}

    public record CreateEmployeeRequest(
            @NotBlank @Size(max = 20) String registrationNo,
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotNull Role role,
            @Size(max = 100) String title,
            Long departmentId,
            @NotNull @PastOrPresent LocalDate hireDate) {}

    public record EmployeeView(Long id, String registrationNo, String fullName, String email, Role role, String title,
            Long departmentId, String department, LocalDate hireDate, boolean active) {}

    // --- İzin
    public record LeaveCreateRequest(
            @NotNull LeaveType type,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @Size(max = 500) String reason) {}

    public record LeaveView(Long id, Long employeeId, String employeeName, LeaveType type, LocalDate startDate,
            LocalDate endDate, int businessDays, LeaveStatus status, String reason, Long decidedBy,
            LocalDateTime decidedAt, String decisionNote) {}

    /** Onay/red kararına isteğe bağlı gerekçe. */
    public record DecisionRequest(@Size(max = 500) String note) {}

    public record LeaveBalance(int year, int entitlement, long used, long pending, long remaining) {}

    // --- Zimmet
    public record CreateAssetRequest(
            @NotBlank @Size(max = 50) String assetTag,
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 50) String category) {}

    public record AssetView(Long id, String assetTag, String name, String category, AssetStatus status) {}

    public record AssignAssetRequest(@NotNull Long employeeId, @Size(max = 300) String note) {}

    public record ReturnAssetRequest(@Size(max = 300) String note) {}

    public record AssignmentView(Long id, Long assetId, String assetTag, String assetName, Long employeeId,
            String employeeName, LocalDateTime assignedAt, LocalDateTime returnedAt, String note) {}

    // --- Rapor
    public record DepartmentLeaveUsage(String department, long employees, long annualDaysUsed, long pendingRequests) {}
}
