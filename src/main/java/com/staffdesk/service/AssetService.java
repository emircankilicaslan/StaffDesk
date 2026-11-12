package com.staffdesk.service;

import com.staffdesk.api.CurrentUser;
import com.staffdesk.api.Dtos.AssetView;
import com.staffdesk.api.Dtos.AssignAssetRequest;
import com.staffdesk.api.Dtos.AssignmentView;
import com.staffdesk.api.Dtos.CreateAssetRequest;
import com.staffdesk.api.Dtos.ReturnAssetRequest;
import com.staffdesk.domain.Asset;
import com.staffdesk.domain.AssetAssignment;
import com.staffdesk.domain.AssetStatus;
import com.staffdesk.domain.Employee;
import com.staffdesk.repository.AssetAssignmentRepository;
import com.staffdesk.repository.AssetRepository;
import com.staffdesk.repository.EmployeeRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetService {

    private final AssetRepository assets;
    private final AssetAssignmentRepository assignments;
    private final EmployeeRepository employees;
    private final AccessPolicy access;
    private final Clock clock;

    public AssetService(AssetRepository assets, AssetAssignmentRepository assignments, EmployeeRepository employees,
            AccessPolicy access, Clock clock) {
        this.assets = assets;
        this.assignments = assignments;
        this.employees = employees;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public AssetView create(CreateAssetRequest r) {
        String tag = r.assetTag().trim();
        if (assets.existsByAssetTag(tag)) {
            throw new BusinessRuleException("Bu demirbaş numarası zaten kayıtlı");
        }
        return toView(assets.save(new Asset(tag, r.name().trim(), r.category().trim())));
    }

    @Transactional(readOnly = true)
    public Page<AssetView> list(AssetStatus status, Pageable pageable) {
        Page<Asset> page = status == null ? assets.findAll(pageable) : assets.findByStatus(status, pageable);
        return page.map(this::toView);
    }

    /** Satır kilidi sayesinde aynı demirbaşa eşzamanlı iki zimmet isteğinden yalnızca biri başarılı olur. */
    @Transactional
    public AssignmentView assign(Long assetId, AssignAssetRequest r) {
        Asset asset = assets.findByIdForUpdate(assetId).orElseThrow(() -> new NotFoundException("Demirbaş bulunamadı"));
        Employee employee = employees.findById(r.employeeId())
                .orElseThrow(() -> new NotFoundException("Personel bulunamadı"));
        if (!employee.isActive()) {
            throw new BusinessRuleException("Pasif personele zimmet verilemez");
        }
        asset.markAssigned();
        AssetAssignment a = assignments.save(new AssetAssignment(asset, employee, LocalDateTime.now(clock), r.note()));
        return toView(a);
    }

    @Transactional
    public AssignmentView giveBack(Long assetId, ReturnAssetRequest r) {
        Asset asset = assets.findByIdForUpdate(assetId).orElseThrow(() -> new NotFoundException("Demirbaş bulunamadı"));
        asset.markReturned();
        AssetAssignment open = assignments.findByAssetIdAndReturnedAtIsNull(assetId)
                .orElseThrow(() -> new BusinessRuleException("Açık zimmet kaydı bulunamadı"));
        open.close(LocalDateTime.now(clock), r == null ? null : r.note());
        return toView(open);
    }

    @Transactional
    public AssetView retire(Long assetId) {
        Asset asset = assets.findByIdForUpdate(assetId).orElseThrow(() -> new NotFoundException("Demirbaş bulunamadı"));
        asset.retire();
        return toView(asset);
    }

    /** Bir personelin zimmet geçmişi (iade edilenler dahil). */
    @Transactional(readOnly = true)
    public List<AssignmentView> historyOf(Long employeeId, CurrentUser user) {
        Employee employee = employees.findById(employeeId).orElseThrow(() -> new NotFoundException("Personel bulunamadı"));
        access.requireCanView(user, employee);
        return assignments.findByEmployeeIdOrderByAssignedAtDesc(employeeId).stream().map(this::toView).toList();
    }

    private AssetView toView(Asset a) {
        return new AssetView(a.getId(), a.getAssetTag(), a.getName(), a.getCategory(), a.getStatus());
    }

    private AssignmentView toView(AssetAssignment a) {
        return new AssignmentView(a.getId(), a.getAsset().getId(), a.getAsset().getAssetTag(), a.getAsset().getName(),
                a.getEmployee().getId(), a.getEmployee().getFullName(), a.getAssignedAt(), a.getReturnedAt(),
                a.getNote());
    }
}
